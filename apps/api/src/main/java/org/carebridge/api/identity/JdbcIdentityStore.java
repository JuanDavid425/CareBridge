package org.carebridge.api.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** PostgreSQL-backed {@link IdentityStore}. Parameterized queries only. */
@Repository
@ConditionalOnProperty(name = "carebridge.identity.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcIdentityStore implements IdentityStore {

    private static final String ACCOUNT_COLUMNS =
            "SELECT id, handle, email, display_name, role, password_hash, active FROM users ";

    private final JdbcClient jdbc;

    public JdbcIdentityStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findByHandle(String handle) {
        return jdbc.sql(ACCOUNT_COLUMNS + "WHERE lower(handle) = lower(:handle)")
                .param("handle", handle)
                .query(this::row)
                .optional()
                .map(this::withAccess);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jdbc.sql(ACCOUNT_COLUMNS + "WHERE id = :id")
                .param("id", id)
                .query(this::row)
                .optional()
                .map(this::withAccess);
    }

    @Override
    public List<RolePermission> roleMatrix() {
        return jdbc.sql("SELECT role, permission, scope FROM role_permissions ORDER BY role, permission")
                .query((rs, n) -> new RolePermission(
                        Role.fromCode(rs.getString("role")),
                        rs.getString("permission"),
                        RolePermission.Scope.valueOf(rs.getString("scope").toUpperCase(Locale.ROOT))))
                .list();
    }

    private Account row(ResultSet rs, int rowNum) throws SQLException {
        return new Account(
                rs.getObject("id", UUID.class),
                rs.getString("handle"),
                rs.getString("email"),
                rs.getString("display_name"),
                Role.fromCode(rs.getString("role")),
                rs.getString("password_hash"),
                rs.getBoolean("active"),
                List.of(),
                Set.of());
    }

    private Account withAccess(Account account) {
        List<UnitMembership> memberships = jdbc.sql("""
                        SELECT m.care_unit_id, u.code, u.name, m.role
                          FROM team_memberships m
                          JOIN care_units u ON u.id = m.care_unit_id
                         WHERE m.user_id = :userId AND m.active AND u.active
                         ORDER BY u.code
                        """)
                .param("userId", account.id())
                .query((rs, n) -> new UnitMembership(
                        rs.getObject("care_unit_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        Role.fromCode(rs.getString("role"))))
                .list();
        List<String> permissions = jdbc.sql(
                        "SELECT permission FROM role_permissions WHERE role = :role ORDER BY permission")
                .param("role", account.role().code())
                .query(String.class)
                .list();
        return new Account(account.id(), account.handle(), account.email(), account.displayName(),
                account.role(), account.passwordHash(), account.active(), memberships,
                Set.copyOf(permissions));
    }
}
