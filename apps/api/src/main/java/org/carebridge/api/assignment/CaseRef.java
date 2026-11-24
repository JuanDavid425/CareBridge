package org.carebridge.api.assignment;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The facts about a case that access decisions need, and nothing else: no patient details.
 *
 * @param caseId         the case
 * @param careUnitId     the care unit that owns the case
 * @param careTeam       staff accounts assigned to the case (coordinators and reviewers)
 * @param portalAccounts patient and caregiver accounts linked to the case
 */
public record CaseRef(UUID caseId, UUID careUnitId, Set<UUID> careTeam, Set<UUID> portalAccounts) {

    public CaseRef {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(careUnitId, "careUnitId");
        careTeam = Set.copyOf(careTeam);
        portalAccounts = Set.copyOf(portalAccounts);
    }
}
