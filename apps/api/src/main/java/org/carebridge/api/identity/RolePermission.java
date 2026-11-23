package org.carebridge.api.identity;

/** One row of the role matrix ({@code role_permissions}). */
public record RolePermission(Role role, String permission, Scope scope) {

    public enum Scope {
        UNIT,
        OWN_CASE,
        GLOBAL
    }
}
