package org.carebridge.api.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.carebridge.api.assignment.CaseScope;
import org.carebridge.api.identity.DemoSeedImporter;
import org.carebridge.api.identity.Role;
import org.carebridge.api.testsupport.PostgresForTests;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V4 schema constraints, {@link JdbcCaseStore} and the case part of the demo seed, on PostgreSQL.
 * "Today" is 2026-03-02 for the seed; queries use the same date.
 */
class CaseStoreIT {

    private static final Path SEED = Path.of("../../packages/synthetic-data/seed/demo-seed.json");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 2);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-02T09:00:00Z"), ZoneOffset.UTC);

    private static PostgresForTests postgres;
    private static JdbcClient jdbc;
    private static JdbcCaseStore store;
    private static DemoSeedImporter importer;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void start() throws Exception {
        postgres = PostgresForTests.startMigrated();
        jdbc = JdbcClient.create(postgres.dataSource());
        transactions = new TransactionTemplate(new DataSourceTransactionManager(postgres.dataSource()));
        importer = new DemoSeedImporter(jdbc, PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                transactions, CLOCK);
        importer.importFile(SEED, "local-demo-password");
        store = transactional(new JdbcCaseStore(jdbc));
    }

    @AfterAll
    static void stop() {
        if (postgres != null) {
            postgres.close();
        }
    }

    /** Runs each store call in a transaction, like the Spring proxy does in the app. */
    private static JdbcCaseStore transactional(JdbcCaseStore target) {
        return new JdbcCaseStore(jdbc) {
            private <T> T tx(Callable<T> call) {
                return transactions.execute(status -> {
                    try {
                        return call.call();
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            }

            @Override
            public DischargeCase create(NewCase request, UUID actorId) {
                return tx(() -> target.create(request, actorId));
            }

            @Override
            public DischargeCase update(UUID id, int expectedVersion, CaseChanges changes, List<String> fields,
                    UUID actorId) {
                return tx(() -> target.update(id, expectedVersion, changes, fields, actorId));
            }

            @Override
            public DischargeCase changeStatus(UUID id, int expectedVersion, CaseStatus from, CaseStatus to,
                    UUID actorId) {
                return tx(() -> target.changeStatus(id, expectedVersion, from, to, actorId));
            }
        };
    }

    private static UUID user(String handle) {
        return jdbc.sql("SELECT id FROM users WHERE handle = :h").param("h", handle).query(UUID.class).single();
    }

    private static UUID unit(String code) {
        return jdbc.sql("SELECT id FROM care_units WHERE code = :c").param("c", code).query(UUID.class).single();
    }

    private static UUID caseId(String reference) {
        return jdbc.sql("SELECT id FROM discharge_cases WHERE reference = :r").param("r", reference)
                .query(UUID.class).single();
    }

    private static CaseScope careTeam(String handle, String... units) {
        return CaseScope.careTeam(user(handle), Set.of(units).stream().map(CaseStoreIT::unit)
                .collect(java.util.stream.Collectors.toSet()));
    }

    private static CaseQuery all() {
        return new CaseQuery(null, Set.of(), null, 0, 50);
    }

    @Test
    void seedLoadsCasesWithCareTeamsLinksAndRelativeDates() {
        DischargeCase c8 = store.findById(caseId("DEMO-CASE-008")).orElseThrow();
        assertThat(c8.careUnitCode()).isEqualTo("DEMO-UNIT-01");
        assertThat(c8.dischargeDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(c8.followUpDue()).isEqualTo(TODAY.plusDays(9));
        assertThat(c8.patient().displayName()).endsWith("(DEMO)");
        assertThat(c8.careTeam()).extracting(CaseMember::role).containsExactly(Role.COORDINATOR, Role.REVIEWER);
        assertThat(c8.portalAccounts()).containsExactlyInAnyOrder(user("alvarez.patient"), user("alvarez.caregiver"));
        assertThat(store.events(c8.id())).extracting(CaseEvent::type).contains(CaseEvent.SEEDED);
    }

    @Test
    void reimportingAnUnchangedSeedKeepsVersions() throws Exception {
        Map<String, Object> before = jdbc.sql("SELECT sum(version) AS v, count(*) AS n FROM case_events")
                .query().singleRow();
        importer.importFile(SEED, "local-demo-password");
        Map<String, Object> after = jdbc.sql("SELECT sum(version) AS v, count(*) AS n FROM case_events")
                .query().singleRow();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void searchHonoursTheScopeAndFilters() {
        CasePage maya = store.search(careTeam("maya.coordinator", "DEMO-UNIT-01"), all(), TODAY);
        assertThat(maya.items()).extracting(DischargeCase::careUnitCode).containsOnly("DEMO-UNIT-01");
        assertThat(maya.items()).allSatisfy(c -> assertThat(c.careTeam())
                .extracting(CaseMember::userId).contains(user("maya.coordinator")));
        assertThat(maya.items()).extracting(DischargeCase::followUpDue).isSorted();

        // Same account, but a scope without unit 01 (membership removed): nothing.
        assertThat(store.search(careTeam("maya.coordinator", "DEMO-UNIT-02"), all(), TODAY).totalItems()).isZero();

        CasePage portal = store.search(CaseScope.ownCase(user("alvarez.patient")), all(), TODAY);
        assertThat(portal.items()).extracting(DischargeCase::reference).containsExactly("DEMO-CASE-008");

        CasePage overdue = store.search(careTeam("omar.coordinator", "DEMO-UNIT-02", "DEMO-UNIT-03"),
                new CaseQuery(null, Set.of(), DueFilter.OVERDUE, 0, 50), TODAY);
        assertThat(overdue.items()).isNotEmpty()
                .allSatisfy(c -> assertThat(c.isOverdue(TODAY)).isTrue());

        CasePage byName = store.search(careTeam("maya.coordinator", "DEMO-UNIT-01"),
                new CaseQuery("case-008", Set.of(CaseStatus.OPEN), null, 0, 50), TODAY);
        assertThat(byName.items()).extracting(DischargeCase::reference).containsExactly("DEMO-CASE-008");
        assertThat(store.search(careTeam("maya.coordinator", "DEMO-UNIT-01"),
                new CaseQuery("%", Set.of(), null, 0, 50), TODAY).totalItems()).isZero();

        CasePage paged = store.search(careTeam("maya.coordinator", "DEMO-UNIT-01"),
                new CaseQuery(null, Set.of(), null, 1, 2), TODAY);
        assertThat(paged.totalItems()).isEqualTo(maya.totalItems());
        assertThat(paged.items()).containsExactlyElementsOf(maya.items().subList(2, Math.min(4, maya.items().size())));
    }

    @Test
    void createUpdateAndStatusChangesAreVersionedAndAudited() {
        UUID maya = user("maya.coordinator");
        UUID unit01 = unit("DEMO-UNIT-01");
        DischargeCase created = store.create(new NewCase(unit01, "Ana Ruiz (DEMO)", "50-59", TODAY,
                TODAY.plusDays(7), "", List.of(new CaseMember(maya, "Maya", Role.COORDINATOR),
                        new CaseMember(user("okafor.reviewer"), "Okafor", Role.REVIEWER))), maya);
        assertThat(created.reference()).matches("DEMO-CASE-1\\d{3}");
        assertThat(created.patient().reference()).matches("DEMO-PT-1\\d{3}");
        assertThat(created.status()).isEqualTo(CaseStatus.OPEN);
        assertThat(created.version()).isEqualTo(1);

        DischargeCase updated = store.update(created.id(), 1,
                new CaseChanges(TODAY, TODAY.plusDays(10), "Invented note."), List.of("followUpDue", "summary"), maya);
        assertThat(updated.version()).isEqualTo(2);
        assertThatThrownBy(() -> store.update(created.id(), 1,
                new CaseChanges(TODAY, TODAY.plusDays(3), ""), List.of("followUpDue"), maya))
                .isInstanceOfSatisfying(StaleCaseVersionException.class, e -> assertThat(e.currentVersion()).isEqualTo(2));
        assertThatThrownBy(() -> store.changeStatus(created.id(), 2, CaseStatus.PLANNING, CaseStatus.REVIEW, maya))
                .isInstanceOf(StaleCaseVersionException.class);
        assertThatThrownBy(() -> store.update(UUID.randomUUID(), 1,
                new CaseChanges(TODAY, TODAY, ""), List.of(), maya))
                .isInstanceOf(CaseNotFoundException.class);

        DischargeCase planning = store.changeStatus(created.id(), 2, CaseStatus.OPEN, CaseStatus.PLANNING, maya);
        assertThat(planning.status()).isEqualTo(CaseStatus.PLANNING);
        assertThat(planning.version()).isEqualTo(3);

        List<CaseEvent> events = store.events(created.id());
        assertThat(events).extracting(CaseEvent::type)
                .containsExactly(CaseEvent.CREATED, CaseEvent.UPDATED, CaseEvent.STATUS_CHANGED);
        assertThat(events.get(1).changedFields()).containsExactly("followUpDue", "summary");
        assertThat(events.get(2).fromStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(events).extracting(CaseEvent::actorName).containsOnly("Maya Lindqvist (DEMO)");
    }

    @Test
    void concurrentWritersWithTheSameVersionCannotBothWin() throws Exception {
        UUID maya = user("maya.coordinator");
        DischargeCase c = store.create(new NewCase(unit("DEMO-UNIT-01"), "Bruno Keller (DEMO)", "40-49", TODAY,
                TODAY.plusDays(7), "", List.of(new CaseMember(maya, "Maya", Role.COORDINATOR))), maya);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String summary = "Writer " + i;
                results.add(pool.submit(() -> {
                    try {
                        store.update(c.id(), 1, new CaseChanges(TODAY, TODAY.plusDays(7), summary),
                                List.of("summary"), maya);
                        return true;
                    } catch (StaleCaseVersionException e) {
                        return false;
                    }
                }));
            }
            long wins = 0;
            for (Future<Boolean> result : results) {
                wins += result.get() ? 1 : 0;
            }
            assertThat(wins).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
        assertThat(store.findById(c.id()).orElseThrow().version()).isEqualTo(2);
        assertThat(store.events(c.id())).hasSize(2);
    }

    @Test
    void schemaRejectsInconsistentRows() {
        UUID caseId = caseId("DEMO-CASE-004");
        UUID patient = jdbc.sql("SELECT patient_id FROM discharge_cases WHERE id = :id").param("id", caseId)
                .query(UUID.class).single();
        UUID unit01 = unit("DEMO-UNIT-01");
        assertRejected("UPDATE discharge_cases SET status = 'archived' WHERE id = '" + caseId + "'");
        assertRejected("UPDATE discharge_cases SET follow_up_due = discharge_date - 1 WHERE id = '" + caseId + "'");
        assertRejected("UPDATE discharge_cases SET synthetic_marker = false WHERE id = '" + caseId + "'");
        assertRejected("UPDATE discharge_cases SET status = 'closed' WHERE id = '" + caseId + "'"); // no closed_at
        assertRejected("UPDATE patients SET display_name = 'Real Name' WHERE id = '" + patient + "'");
        assertRejected("INSERT INTO patients (reference, display_name, age_band) VALUES ('PT-1', 'X (DEMO)', '40-49')");
        // Second open case for the same patient.
        assertRejected("INSERT INTO discharge_cases (reference, care_unit_id, patient_id, discharge_date, follow_up_due)"
                + " VALUES ('DEMO-CASE-999', '" + unit01 + "', '" + patient + "', DATE '2026-03-02', DATE '2026-03-09')");
        // Reviewer of unit 02/03 on a unit 01 case: no membership in that unit.
        assertRejected("INSERT INTO case_assignments (case_id, user_id, care_unit_id, role) VALUES ('" + caseId
                + "', '" + user("reyes.reviewer") + "', '" + unit01 + "', 'reviewer')");
        // Membership role must match the account's role.
        assertRejected("INSERT INTO case_portal_links (case_id, user_id, role) VALUES ('" + caseId
                + "', '" + user("maya.coordinator") + "', 'patient')");
        assertRejected("UPDATE case_events SET type = 'updated' WHERE case_id = '" + caseId + "'");
        assertRejected("DELETE FROM case_events WHERE case_id = '" + caseId + "'");
    }

    private static void assertRejected(String sql) {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> jdbc.sql(sql).update()))
                .as(sql)
                .isInstanceOf(DataAccessException.class);
    }
}
