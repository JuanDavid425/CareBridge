package org.carebridge.api.cases;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Follow-up due-date filters for the case list, relative to "today". Closed cases are never
 * overdue or due.
 */
public enum DueFilter {
    /** Follow-up date already passed. */
    OVERDUE,
    /** Due today. */
    TODAY,
    /** Due within the next seven days, today included. */
    WEEK;

    public static Optional<DueFilter> parse(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(d -> d.code().equals(code.strip())).findFirst();
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Inclusive lower bound, or {@code null} when open-ended. */
    public LocalDate from(LocalDate today) {
        return this == OVERDUE ? null : today;
    }

    /** Inclusive upper bound. */
    public LocalDate to(LocalDate today) {
        return switch (this) {
            case OVERDUE -> today.minusDays(1);
            case TODAY -> today;
            case WEEK -> today.plusDays(6);
        };
    }

    public boolean matches(LocalDate followUpDue, LocalDate today) {
        LocalDate from = from(today);
        return (from == null || !followUpDue.isBefore(from)) && !followUpDue.isAfter(to(today));
    }
}
