package org.carebridge.api.cases;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lifecycle of a discharge case (see docs/product/workflows.md):
 *
 * <pre>
 * open → planning → review → active → closed
 *                ↖ review (changes requested)
 * </pre>
 *
 * Each legal move names the permission it needs. Coordinators run intake and planning and close
 * cases; reviewers decide what leaves review. Anything else, shortcuts included, is rejected.
 */
public enum CaseStatus {
    OPEN,
    PLANNING,
    REVIEW,
    ACTIVE,
    CLOSED;

    public static final String UPDATE = "case.update";
    public static final String PUBLISH = "plan.publish";

    private static final Map<CaseStatus, Map<CaseStatus, String>> TRANSITIONS = Map.of(
            OPEN, Map.of(PLANNING, UPDATE),
            PLANNING, Map.of(REVIEW, UPDATE),
            REVIEW, Map.of(PLANNING, PUBLISH, ACTIVE, PUBLISH),
            ACTIVE, Map.of(CLOSED, UPDATE),
            CLOSED, Map.of());

    /** Parses the lower-case wire value; empty for anything else. */
    public static Optional<CaseStatus> parse(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.code().equals(code.strip())).findFirst();
    }

    public static CaseStatus fromCode(String code) {
        return parse(code).orElseThrow(() -> new IllegalArgumentException("unknown case status"));
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The permission the move needs, or empty when the move is not part of the lifecycle. */
    public Optional<String> permissionTo(CaseStatus target) {
        return Optional.ofNullable(TRANSITIONS.get(this).get(target));
    }

    /** Statuses reachable in one step, in lifecycle order. */
    public List<CaseStatus> next() {
        return Arrays.stream(values()).filter(s -> TRANSITIONS.get(this).containsKey(s)).toList();
    }

    public boolean isClosed() {
        return this == CLOSED;
    }
}
