package org.carebridge.api.cases;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One entry of a case's append-only history. Changed fields are listed by name only.
 *
 * @param actorId null for changes made by the demo seed
 */
public record CaseEvent(
        long id,
        UUID caseId,
        UUID actorId,
        String actorName,
        String type,
        CaseStatus fromStatus,
        CaseStatus toStatus,
        List<String> changedFields,
        int version,
        Instant occurredAt) {

    public static final String CREATED = "created";
    public static final String UPDATED = "updated";
    public static final String STATUS_CHANGED = "status_changed";
    public static final String SEEDED = "seeded";

    public CaseEvent {
        changedFields = List.copyOf(changedFields);
    }
}
