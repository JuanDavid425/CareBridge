package org.carebridge.api.identity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A user account with its active unit memberships and the permissions of its role.
 *
 * @param passwordHash delegating-encoder hash, or {@code null} when the account cannot sign in
 */
public record Account(
        UUID id,
        String handle,
        String email,
        String displayName,
        Role role,
        String passwordHash,
        boolean active,
        List<UnitMembership> memberships,
        Set<String> permissions) {

    public Account {
        memberships = List.copyOf(memberships);
        permissions = Set.copyOf(permissions);
    }

    public boolean canSignIn() {
        return active && passwordHash != null;
    }
}
