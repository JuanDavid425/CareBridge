package org.carebridge.api.cases;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.carebridge.api.assignment.CaseScope;
import org.carebridge.api.identity.Role;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed {@link CaseStore}. Parameterized queries only; search text is bound as a LIKE
 * pattern with its wildcards escaped. Version checks are part of each UPDATE's WHERE clause, so two
 * writers holding the same version cannot both succeed.
 */
@Repository
@ConditionalOnProperty(name = "carebridge.cases.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcCaseStore implements CaseStore {

    private static final String CASE_COLUMNS = """
            SELECT c.id, c.reference, c.care_unit_id, u.code AS unit_code, u.name AS unit_name,
                   p.id AS patient_id, p.reference AS patient_reference, p.display_name, p.age_band,
                   c.status, c.discharge_date, c.follow_up_due, c.summary, c.version,
                   c.created_at, c.updated_at
              FROM discharge_cases c
              JOIN care_units u ON u.id = c.care_unit_id
              JOIN patients p ON p.id = c.patient_id
            """;

    private final JdbcClient jdbc;

    public JdbcCaseStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A case row before its care team and portal links are attached. */
    private record Row(UUID id, String reference, UUID unitId, String unitCode, String unitName,
            PatientInfo patient, CaseStatus status, LocalDate dischargeDate, LocalDate followUpDue,
            String summary, int version, Instant createdAt, Instant updatedAt) {
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DischargeCase> findById(UUID id) {
        return jdbc.sql(CASE_COLUMNS + " WHERE c.id = :id")
                .param("id", id)
                .query(this::row)
                .optional()
                .map(row -> attach(List.of(row)).get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public CasePage search(CaseScope scope, CaseQuery query, LocalDate today) {
        if (scope.isEmpty()) {
            return CasePage.empty(query);
        }
        StringBuilder where = new StringBuilder(" WHERE true");
        Map<String, Object> params = new HashMap<>();
        params.put("account", scope.accountId());
        switch (scope.kind()) {
            case CARE_TEAM -> {
                where.append(" AND c.care_unit_id IN (:units)")
                        .append(" AND EXISTS (SELECT 1 FROM case_assignments a")
                        .append(" WHERE a.case_id = c.id AND a.user_id = :account)");
                params.put("units", List.copyOf(scope.careUnitIds()));
            }
            case OWN_CASE -> where.append(" AND EXISTS (SELECT 1 FROM case_portal_links l")
                    .append(" WHERE l.case_id = c.id AND l.user_id = :account)");
            case NONE -> throw new IllegalStateException("empty scope");
        }
        if (!query.statuses().isEmpty()) {
            where.append(" AND c.status IN (:statuses)");
            params.put("statuses", query.statuses().stream().map(CaseStatus::code).sorted().toList());
        }
        if (query.search() != null) {
            where.append(" AND (lower(c.reference) LIKE :pattern ESCAPE '\\'")
                    .append(" OR lower(p.display_name) LIKE :pattern ESCAPE '\\')");
            params.put("pattern", "%" + escapeLike(query.searchKey()) + "%");
        }
        if (query.due() != null) {
            where.append(" AND c.status <> 'closed' AND c.follow_up_due <= :dueTo");
            params.put("dueTo", query.due().to(today));
            LocalDate from = query.due().from(today);
            if (from != null) {
                where.append(" AND c.follow_up_due >= :dueFrom");
                params.put("dueFrom", from);
            }
        }

        String from = " FROM discharge_cases c JOIN patients p ON p.id = c.patient_id" + where;
        long total = jdbc.sql("SELECT count(*)" + from).params(params).query(Long.class).single();
        if (total == 0 || query.offset() >= total) {
            return new CasePage(List.of(), query.page(), query.size(), total);
        }
        params.put("limit", query.size());
        params.put("offset", query.offset());
        List<Row> rows = jdbc.sql(CASE_COLUMNS + where
                        + " ORDER BY c.follow_up_due, c.reference LIMIT :limit OFFSET :offset")
                .params(params)
                .query(this::row)
                .list();
        return new CasePage(attach(rows), query.page(), query.size(), total);
    }

    @Override
    @Transactional
    public DischargeCase create(NewCase request, UUID actorId) {
        UUID patientId = jdbc.sql("""
                        INSERT INTO patients (reference, display_name, age_band)
                        VALUES ('DEMO-PT-' || nextval('patient_reference_seq'), :name, :ageBand)
                        RETURNING id
                        """)
                .param("name", request.patientName())
                .param("ageBand", request.ageBand())
                .query(UUID.class)
                .single();
        UUID caseId = jdbc.sql("""
                        INSERT INTO discharge_cases
                            (reference, care_unit_id, patient_id, discharge_date, follow_up_due, summary, created_by)
                        VALUES ('DEMO-CASE-' || nextval('case_reference_seq'), :unit, :patient,
                                :discharge, :due, :summary, :actor)
                        RETURNING id
                        """)
                .param("unit", request.careUnitId())
                .param("patient", patientId)
                .param("discharge", request.dischargeDate())
                .param("due", request.followUpDue())
                .param("summary", request.summary())
                .param("actor", actorId)
                .query(UUID.class)
                .single();
        for (CaseMember member : request.careTeam()) {
            jdbc.sql("""
                            INSERT INTO case_assignments (case_id, user_id, care_unit_id, role)
                            VALUES (:case, :user, :unit, :role)
                            """)
                    .param("case", caseId)
                    .param("user", member.userId())
                    .param("unit", request.careUnitId())
                    .param("role", member.role().code())
                    .update();
        }
        appendEvent(caseId, actorId, CaseEvent.CREATED, null, null, List.of(), 1);
        return findById(caseId).orElseThrow();
    }

    @Override
    @Transactional
    public DischargeCase update(UUID id, int expectedVersion, CaseChanges changes, List<String> changedFields,
            UUID actorId) {
        Optional<Integer> version = jdbc.sql("""
                        UPDATE discharge_cases
                           SET discharge_date = :discharge, follow_up_due = :due, summary = :summary,
                               version = version + 1, updated_at = now()
                         WHERE id = :id AND version = :expected AND status <> 'closed'
                        RETURNING version
                        """)
                .param("discharge", changes.dischargeDate())
                .param("due", changes.followUpDue())
                .param("summary", changes.summary())
                .param("id", id)
                .param("expected", expectedVersion)
                .query(Integer.class)
                .optional();
        if (version.isEmpty()) {
            throw notWritten(id, expectedVersion);
        }
        appendEvent(id, actorId, CaseEvent.UPDATED, null, null, changedFields, version.get());
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public DischargeCase changeStatus(UUID id, int expectedVersion, CaseStatus from, CaseStatus to, UUID actorId) {
        Optional<Integer> version = jdbc.sql("""
                        UPDATE discharge_cases
                           SET status = :to,
                               closed_at = CASE WHEN :to = 'closed' THEN now() END,
                               version = version + 1, updated_at = now()
                         WHERE id = :id AND version = :expected AND status = :from
                        RETURNING version
                        """)
                .param("to", to.code())
                .param("from", from.code())
                .param("id", id)
                .param("expected", expectedVersion)
                .query(Integer.class)
                .optional();
        if (version.isEmpty()) {
            throw notWritten(id, expectedVersion);
        }
        appendEvent(id, actorId, CaseEvent.STATUS_CHANGED, from, to, List.of(), version.get());
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseEvent> events(UUID caseId) {
        return jdbc.sql("""
                        SELECT e.id, e.case_id, e.actor_id, u.display_name AS actor_name, e.type,
                               e.from_status, e.to_status, e.changed_fields, e.version, e.occurred_at
                          FROM case_events e
                          LEFT JOIN users u ON u.id = e.actor_id
                         WHERE e.case_id = :case
                         ORDER BY e.id
                        """)
                .param("case", caseId)
                .query((rs, n) -> new CaseEvent(
                        rs.getLong("id"),
                        rs.getObject("case_id", UUID.class),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("actor_name"),
                        rs.getString("type"),
                        status(rs.getString("from_status")),
                        status(rs.getString("to_status")),
                        textArray(rs.getArray("changed_fields")),
                        rs.getInt("version"),
                        instant(rs, "occurred_at")))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseMember> activeMembers(UUID careUnitId, Role role) {
        return jdbc.sql("""
                        SELECT u.id, u.display_name
                          FROM team_memberships m
                          JOIN users u ON u.id = m.user_id
                          JOIN care_units cu ON cu.id = m.care_unit_id
                         WHERE m.care_unit_id = :unit AND m.role = :role AND m.active AND u.active AND cu.active
                         ORDER BY u.display_name, u.id
                        """)
                .param("unit", careUnitId)
                .param("role", role.code())
                .query((rs, n) -> new CaseMember(rs.getObject("id", UUID.class), rs.getString("display_name"), role))
                .list();
    }

    /** After a conditional UPDATE matched nothing: missing, stale, or (for edits) closed. */
    private RuntimeException notWritten(UUID id, int expectedVersion) {
        Optional<Map<String, Object>> current = jdbc.sql("SELECT version, status FROM discharge_cases WHERE id = :id")
                .param("id", id)
                .query()
                .listOfRows()
                .stream()
                .findFirst();
        if (current.isEmpty()) {
            return new CaseNotFoundException();
        }
        int version = ((Number) current.get().get("version")).intValue();
        if (version != expectedVersion) {
            return new StaleCaseVersionException(version);
        }
        if ("closed".equals(current.get().get("status"))) {
            return new CaseConflictException(CaseConflictException.CASE_CLOSED, "Closed cases cannot be edited.");
        }
        return new StaleCaseVersionException(version);
    }

    private void appendEvent(UUID caseId, UUID actorId, String type, CaseStatus from, CaseStatus to,
            List<String> changedFields, int version) {
        jdbc.sql("""
                        INSERT INTO case_events (case_id, actor_id, type, from_status, to_status, changed_fields, version)
                        VALUES (:case, :actor, :type, :from, :to,
                                COALESCE(string_to_array(NULLIF(:fields, ''), ','), '{}'), :version)
                        """)
                .param("case", caseId)
                .param("actor", actorId)
                .param("type", type)
                .param("from", from == null ? null : from.code())
                .param("to", to == null ? null : to.code())
                .param("fields", String.join(",", changedFields))
                .param("version", version)
                .update();
    }

    /** Care teams and portal links for a batch of rows, keeping the batch order. */
    private List<DischargeCase> attach(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(Row::id).toList();
        Map<UUID, List<CaseMember>> teams = new HashMap<>();
        jdbc.sql("""
                        SELECT a.case_id, u.id, u.display_name, a.role
                          FROM case_assignments a
                          JOIN users u ON u.id = a.user_id
                         WHERE a.case_id IN (:ids)
                         ORDER BY CASE a.role WHEN 'coordinator' THEN 0 ELSE 1 END, u.display_name
                        """)
                .param("ids", ids)
                .query((ResultSet rs) -> {
                    teams.computeIfAbsent(rs.getObject("case_id", UUID.class), k -> new ArrayList<>())
                            .add(new CaseMember(rs.getObject("id", UUID.class), rs.getString("display_name"),
                                    Role.fromCode(rs.getString("role"))));
                });
        Map<UUID, Set<UUID>> links = new HashMap<>();
        jdbc.sql("SELECT case_id, user_id FROM case_portal_links WHERE case_id IN (:ids)")
                .param("ids", ids)
                .query((ResultSet rs) -> {
                    links.computeIfAbsent(rs.getObject("case_id", UUID.class), k -> new HashSet<>())
                            .add(rs.getObject("user_id", UUID.class));
                });
        Map<UUID, DischargeCase> byId = new LinkedHashMap<>();
        for (Row row : rows) {
            byId.put(row.id(), new DischargeCase(row.id(), row.reference(), row.unitId(), row.unitCode(),
                    row.unitName(), row.patient(), row.status(), row.dischargeDate(), row.followUpDue(),
                    row.summary(), row.version(), teams.getOrDefault(row.id(), List.of()),
                    links.getOrDefault(row.id(), Set.of()), row.createdAt(), row.updatedAt()));
        }
        return List.copyOf(byId.values());
    }

    private Row row(ResultSet rs, int rowNum) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                rs.getObject("care_unit_id", UUID.class),
                rs.getString("unit_code"),
                rs.getString("unit_name"),
                new PatientInfo(rs.getObject("patient_id", UUID.class), rs.getString("patient_reference"),
                        rs.getString("display_name"), rs.getString("age_band")),
                CaseStatus.fromCode(rs.getString("status")),
                rs.getObject("discharge_date", LocalDate.class),
                rs.getObject("follow_up_due", LocalDate.class),
                rs.getString("summary"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static CaseStatus status(String code) {
        return code == null ? null : CaseStatus.fromCode(code);
    }

    private static List<String> textArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
