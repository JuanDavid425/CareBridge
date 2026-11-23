package org.carebridge.api.testsupport;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.carebridge.api.identity.Account;
import org.carebridge.api.identity.IdentityStore;
import org.carebridge.api.identity.Role;
import org.carebridge.api.identity.RolePermission;
import org.carebridge.api.identity.RolePermission.Scope;
import org.carebridge.api.identity.UnitMembership;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Accounts for web-layer tests, mirroring the demo seed and the V3 role matrix. */
public class InMemoryIdentityStore implements IdentityStore {

    public static final String PASSWORD = "test-password-123";

    public static final UnitMembership UNIT_01_COORDINATOR = membership("DEMO-UNIT-01", Role.COORDINATOR);
    public static final UnitMembership UNIT_01_REVIEWER = membership("DEMO-UNIT-01", Role.REVIEWER);

    static final List<RolePermission> MATRIX = List.of(
            new RolePermission(Role.ADMIN, "audit.read", Scope.GLOBAL),
            new RolePermission(Role.ADMIN, "unit.manage", Scope.GLOBAL),
            new RolePermission(Role.ADMIN, "user.manage", Scope.GLOBAL),
            new RolePermission(Role.CAREGIVER, "plan.read_published", Scope.OWN_CASE),
            new RolePermission(Role.COORDINATOR, "audit.read", Scope.UNIT),
            new RolePermission(Role.COORDINATOR, "case.create", Scope.UNIT),
            new RolePermission(Role.COORDINATOR, "case.read", Scope.UNIT),
            new RolePermission(Role.COORDINATOR, "document.upload", Scope.UNIT),
            new RolePermission(Role.COORDINATOR, "task.assign", Scope.UNIT),
            new RolePermission(Role.COORDINATOR, "task.edit", Scope.UNIT),
            new RolePermission(Role.PATIENT, "plan.read_published", Scope.OWN_CASE),
            new RolePermission(Role.REVIEWER, "audit.read", Scope.UNIT),
            new RolePermission(Role.REVIEWER, "case.read", Scope.UNIT),
            new RolePermission(Role.REVIEWER, "plan.publish", Scope.UNIT),
            new RolePermission(Role.REVIEWER, "proposal.review", Scope.UNIT));

    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();

    public InMemoryIdentityStore(PasswordEncoder encoder) {
        String hash = encoder.encode(PASSWORD);
        add("maya.coordinator", Role.COORDINATOR, hash, true, List.of(UNIT_01_COORDINATOR));
        add("okafor.reviewer", Role.REVIEWER, hash, true, List.of(UNIT_01_REVIEWER));
        add("leaving.coordinator", Role.COORDINATOR, hash, true, List.of(UNIT_01_COORDINATOR));
        add("lee.admin", Role.ADMIN, hash, true, List.of());
        add("alvarez.patient", Role.PATIENT, hash, true, List.of());
        add("former.coordinator", Role.COORDINATOR, hash, false, List.of());
        add("nopassword.reviewer", Role.REVIEWER, null, true, List.of());
    }

    private static UnitMembership membership(String code, Role role) {
        return new UnitMembership(UUID.nameUUIDFromBytes(code.getBytes()), code, code + " (DEMO)", role);
    }

    private void add(String handle, Role role, String hash, boolean active, List<UnitMembership> units) {
        Set<String> permissions = MATRIX.stream()
                .filter(row -> row.role() == role)
                .map(RolePermission::permission)
                .collect(Collectors.toSet());
        Account account = new Account(UUID.nameUUIDFromBytes(handle.getBytes()), handle,
                handle + "@demo.carebridge.invalid", handle + " (DEMO)", role, hash, active, units, permissions);
        accounts.put(account.id(), account);
    }

    /** Simulates an administrator deactivating an account while it is signed in. */
    public void deactivate(String handle) {
        findByHandle(handle).ifPresent(a -> accounts.put(a.id(), new Account(a.id(), a.handle(), a.email(),
                a.displayName(), a.role(), a.passwordHash(), false, a.memberships(), a.permissions())));
    }

    @Override
    public Optional<Account> findByHandle(String handle) {
        return accounts.values().stream().filter(a -> a.handle().equalsIgnoreCase(handle)).findFirst();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public List<RolePermission> roleMatrix() {
        return MATRIX;
    }
}
