package org.carebridge.api.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads care units, demo accounts and memberships from the synthetic seed in one transaction.
 *
 * <p>Idempotent: units and accounts are upserted by their seed ids, and memberships missing from
 * the seed are deactivated rather than deleted. Synthetic patients and the seeded cases
 * ({@code DEMO-CASE-001…}) are reset to the seed on every import, with dates recomputed from their
 * day offsets; a case only gets a new version (and a {@code seeded} event) when something changed.
 * Cases created in the app are never touched.
 */
public class DemoSeedImporter {

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    /** Days from discharge to the follow-up due date for seeded cases. */
    static final int SEED_FOLLOW_UP_DAYS = 7;

    public DemoSeedImporter(JdbcClient jdbc, PasswordEncoder passwordEncoder, TransactionTemplate transactions) {
        this(jdbc, passwordEncoder, transactions, Clock.systemUTC());
    }

    public DemoSeedImporter(JdbcClient jdbc, PasswordEncoder passwordEncoder, TransactionTemplate transactions,
            Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
        this.clock = clock;
    }

    public record Result(int careUnits, int accounts, int memberships, int patients, int cases) {
    }

    private record PortalLink(UUID userId, Role role, List<String> caseRefs) {
    }

    public Result importFile(Path seedFile, String demoPassword) throws IOException {
        checkDemoPassword(demoPassword);
        JsonNode seed = json.readTree(Files.readString(seedFile));
        requireSynthetic(seed);
        return transactions.execute(status -> load(seed, demoPassword));
    }

    /**
     * Demo accounts share one password that comes from the environment. Refuse short values and
     * the {@code change-me…} placeholders from example files, so a copied example never becomes a
     * working credential on a reachable demo instance.
     */
    static void checkDemoPassword(String demoPassword) {
        if (demoPassword == null || demoPassword.isBlank() || demoPassword.length() < 12) {
            throw new IllegalArgumentException(
                    "CAREBRIDGE_DEMO_PASSWORD must be set (at least 12 characters) to seed demo accounts");
        }
        if (demoPassword.strip().toLowerCase(java.util.Locale.ROOT).startsWith("change-me")) {
            throw new IllegalArgumentException(
                    "CAREBRIDGE_DEMO_PASSWORD still has a placeholder value; choose your own local value");
        }
    }

    private static void requireSynthetic(JsonNode seed) {
        if (!"SYNTHETIC_ONLY".equals(seed.path("dataMode").asText())) {
            throw new IllegalArgumentException("seed file is not marked dataMode=SYNTHETIC_ONLY");
        }
        for (String section : new String[] {"careUnits", "users", "patients", "cases"}) {
            for (JsonNode item : seed.path(section)) {
                if (!item.path("id").asText().startsWith("DEMO-")) {
                    throw new IllegalArgumentException("seed id without DEMO- prefix in " + section);
                }
            }
        }
        for (String section : new String[] {"patients", "cases"}) {
            for (JsonNode item : seed.path(section)) {
                if (!item.path("syntheticMarker").asBoolean(false)) {
                    throw new IllegalArgumentException("seed record without syntheticMarker in " + section);
                }
            }
        }
    }

    private Result load(JsonNode seed, String demoPassword) {
        int units = 0;
        for (JsonNode unit : seed.path("careUnits")) {
            jdbc.sql("""
                            INSERT INTO care_units (code, name) VALUES (:code, :name)
                            ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, active = true
                            """)
                    .param("code", unit.path("id").asText())
                    .param("name", unit.path("name").asText())
                    .update();
            units++;
        }

        int accounts = 0;
        int memberships = 0;
        List<PortalLink> portalLinks = new ArrayList<>();
        for (JsonNode user : seed.path("users")) {
            Role role = Role.fromCode(user.path("role").asText());
            UUID userId = jdbc.sql("""
                            INSERT INTO users (external_ref, handle, email, display_name, role, password_hash)
                            VALUES (:ref, :handle, :email, :displayName, :role, :passwordHash)
                            ON CONFLICT (external_ref) DO UPDATE SET
                                handle = EXCLUDED.handle,
                                email = EXCLUDED.email,
                                display_name = EXCLUDED.display_name,
                                role = EXCLUDED.role,
                                password_hash = EXCLUDED.password_hash,
                                active = true,
                                updated_at = now()
                            RETURNING id
                            """)
                    .param("ref", user.path("id").asText())
                    .param("handle", user.path("handle").asText())
                    .param("email", user.path("email").asText())
                    .param("displayName", user.path("displayName").asText())
                    .param("role", role.code())
                    .param("passwordHash", passwordEncoder.encode(demoPassword))
                    .query(UUID.class)
                    .single();
            accounts++;

            jdbc.sql("UPDATE team_memberships SET active = false WHERE user_id = :userId")
                    .param("userId", userId)
                    .update();
            for (JsonNode unitId : user.path("unitIds")) {
                if (!role.isCareTeam()) {
                    throw new IllegalArgumentException(
                            user.path("handle").asText() + " has role " + role.code() + " and cannot join a unit");
                }
                int rows = jdbc.sql("""
                                INSERT INTO team_memberships (user_id, care_unit_id, role)
                                SELECT :userId, id, :role FROM care_units WHERE code = :code
                                ON CONFLICT (user_id, care_unit_id) DO UPDATE SET role = EXCLUDED.role, active = true
                                """)
                        .param("userId", userId)
                        .param("role", role.code())
                        .param("code", unitId.asText())
                        .update();
                if (rows != 1) {
                    throw new IllegalArgumentException("unknown care unit " + unitId.asText());
                }
                memberships++;
            }
            if (!role.isCareTeam()) {
                List<String> caseRefs = new ArrayList<>();
                user.path("linkedCaseIds").forEach(ref -> caseRefs.add(ref.asText()));
                portalLinks.add(new PortalLink(userId, role, caseRefs));
            }
        }

        int patients = 0;
        for (JsonNode patient : seed.path("patients")) {
            jdbc.sql("""
                            INSERT INTO patients (reference, display_name, age_band) VALUES (:ref, :name, :ageBand)
                            ON CONFLICT (reference) DO UPDATE SET display_name = EXCLUDED.display_name,
                                                                  age_band = EXCLUDED.age_band
                            """)
                    .param("ref", patient.path("id").asText())
                    .param("name", patient.path("displayName").asText())
                    .param("ageBand", patient.path("ageBand").asText())
                    .update();
            patients++;
        }

        LocalDate today = LocalDate.now(clock);
        int cases = 0;
        for (JsonNode seedCase : seed.path("cases")) {
            loadCase(seedCase, today);
            cases++;
        }

        for (PortalLink link : portalLinks) {
            jdbc.sql("DELETE FROM case_portal_links WHERE user_id = :userId")
                    .param("userId", link.userId())
                    .update();
            for (String caseRef : link.caseRefs()) {
                int rows = jdbc.sql("""
                                INSERT INTO case_portal_links (case_id, user_id, role)
                                SELECT id, :userId, :role FROM discharge_cases WHERE reference = :ref
                                """)
                        .param("userId", link.userId())
                        .param("role", link.role().code())
                        .param("ref", caseRef)
                        .update();
                if (rows != 1) {
                    throw new IllegalArgumentException("unknown linked case " + caseRef);
                }
            }
        }
        return new Result(units, accounts, memberships, patients, cases);
    }

    /** Upserts one seeded case and replaces its care team. */
    private void loadCase(JsonNode seedCase, LocalDate today) {
        String reference = seedCase.path("id").asText();
        String status = seedCase.path("status").asText();
        String unitCode = seedCase.path("unitId").asText();
        LocalDate discharge = today.plusDays(seedCase.path("dischargeOffsetDays").asInt());

        // The care team's composite key includes the unit, so clear it before the case row changes.
        Optional<UUID> existing = jdbc.sql("SELECT id FROM discharge_cases WHERE reference = :ref")
                .param("ref", reference)
                .query(UUID.class)
                .optional();
        existing.ifPresent(id -> jdbc.sql("DELETE FROM case_assignments WHERE case_id = :id")
                .param("id", id)
                .update());

        Optional<Integer> written = jdbc.sql("""
                        INSERT INTO discharge_cases
                            (reference, care_unit_id, patient_id, status, discharge_date, follow_up_due, closed_at)
                        SELECT :ref, u.id, p.id, :status, :discharge, :due,
                               CASE WHEN :status = 'closed' THEN now() END
                          FROM care_units u, patients p
                         WHERE u.code = :unit AND p.reference = :patient
                        ON CONFLICT (reference) DO UPDATE SET
                            care_unit_id = EXCLUDED.care_unit_id,
                            patient_id = EXCLUDED.patient_id,
                            status = EXCLUDED.status,
                            discharge_date = EXCLUDED.discharge_date,
                            follow_up_due = EXCLUDED.follow_up_due,
                            summary = '',
                            closed_at = CASE WHEN EXCLUDED.status = 'closed'
                                             THEN COALESCE(discharge_cases.closed_at, now()) END,
                            version = discharge_cases.version + 1,
                            updated_at = now()
                          WHERE (discharge_cases.care_unit_id, discharge_cases.patient_id, discharge_cases.status,
                                 discharge_cases.discharge_date, discharge_cases.follow_up_due, discharge_cases.summary)
                                IS DISTINCT FROM
                                (EXCLUDED.care_unit_id, EXCLUDED.patient_id, EXCLUDED.status,
                                 EXCLUDED.discharge_date, EXCLUDED.follow_up_due, '')
                        RETURNING version
                        """)
                .param("ref", reference)
                .param("status", status)
                .param("discharge", discharge)
                .param("due", discharge.plusDays(SEED_FOLLOW_UP_DAYS))
                .param("unit", unitCode)
                .param("patient", seedCase.path("patientId").asText())
                .query(Integer.class)
                .optional();
        UUID caseId = jdbc.sql("SELECT id FROM discharge_cases WHERE reference = :ref")
                .param("ref", reference)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "case " + reference + " refers to an unknown unit or patient"));
        written.ifPresent(version -> jdbc.sql("""
                        INSERT INTO case_events (case_id, actor_id, type, version) VALUES (:id, NULL, 'seeded', :version)
                        """)
                .param("id", caseId)
                .param("version", version)
                .update());

        for (String field : new String[] {"coordinatorId", "reviewerId"}) {
            String userRef = seedCase.path(field).asText(null);
            if (userRef == null) {
                continue;
            }
            int rows = jdbc.sql("""
                            INSERT INTO case_assignments (case_id, user_id, care_unit_id, role)
                            SELECT c.id, u.id, c.care_unit_id, u.role
                              FROM discharge_cases c, users u
                             WHERE c.id = :caseId AND u.external_ref = :userRef
                            """)
                    .param("caseId", caseId)
                    .param("userRef", userRef)
                    .update();
            if (rows != 1) {
                throw new IllegalArgumentException("case " + reference + " refers to an unknown " + field);
            }
        }
    }
}
