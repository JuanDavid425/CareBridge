package org.carebridge.api.cases;

import java.util.Locale;
import java.util.Set;

/**
 * Filters and paging for the case list.
 *
 * @param search   case-insensitive match on case reference or patient display name; null for none
 * @param statuses empty for every status
 * @param due      null for any follow-up date
 * @param page     zero-based
 */
public record CaseQuery(String search, Set<CaseStatus> statuses, DueFilter due, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int MAX_SEARCH = 80;

    public CaseQuery {
        search = search == null || search.isBlank() ? null : search.strip();
        statuses = Set.copyOf(statuses);
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        if (search != null && search.length() > MAX_SEARCH) {
            throw new IllegalArgumentException("search is too long");
        }
    }

    public long offset() {
        return (long) page * size;
    }

    /** Lower-cased search text, for comparisons in memory and in SQL. */
    public String searchKey() {
        return search == null ? null : search.toLowerCase(Locale.ROOT);
    }
}
