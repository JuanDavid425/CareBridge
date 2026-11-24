package org.carebridge.api.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads care units, demo accounts and memberships from the synthetic seed in one transaction.
 *
 * <p>Idempotent: units and accounts are upserted by their seed ids, and memberships missing from
 * the seed are deactivated rather than deleted. Patient/caregiver case links are loaded together
 * with the case registry, once cases exist.
 */
public class DemoSeedImporter {

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;
    private final ObjectMapper json = new ObjectMapper();

    public DemoSeedImporter(JdbcClient jdbc, PasswordEncoder passwordEncoder, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
    }

    public record Result(int careUnits, int accounts, int memberships) {
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
        for (String section : new String[] {"careUnits", "users"}) {
            for (JsonNode item : seed.path(section)) {
                if (!item.path("id").asText().startsWith("DEMO-")) {
                    throw new IllegalArgumentException("seed id without DEMO- prefix in " + section);
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
        }
        return new Result(units, accounts, memberships);
    }
}
