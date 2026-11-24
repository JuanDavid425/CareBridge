package org.carebridge.api.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read access to accounts, memberships and the role matrix. */
public interface IdentityStore {

    /** Case-insensitive lookup; returns inactive accounts too so callers can reject them. */
    Optional<Account> findByHandle(String handle);

    Optional<Account> findById(UUID id);

    List<RolePermission> roleMatrix();
}
