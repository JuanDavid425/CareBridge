package org.carebridge.api.identity;

import java.util.Locale;

/** The single role of an account. Stored in lower case ({@code coordinator}) in the database. */
public enum Role {
    COORDINATOR,
    REVIEWER,
    PATIENT,
    CAREGIVER,
    ADMIN;

    public static Role fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Spring Security authority, e.g. {@code ROLE_COORDINATOR}. */
    public String authority() {
        return "ROLE_" + name();
    }

    /** Care-team roles are the only ones that belong to care units. */
    public boolean isCareTeam() {
        return this == COORDINATOR || this == REVIEWER;
    }
}
