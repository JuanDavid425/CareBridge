package org.carebridge.api.cases;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.carebridge.api.assignment.CaseScope;
import org.carebridge.api.identity.Role;

/**
 * Persistence for the case registry. Writes are conditional on the version the caller saw, so a
 * concurrent edit makes the second writer fail with {@link StaleCaseVersionException} instead of
 * silently overwriting the first. Every write appends a {@link CaseEvent} in the same transaction.
 */
public interface CaseStore {

    Optional<DischargeCase> findById(UUID id);

    /** Cases in {@code scope} matching {@code query}; never a case the single-case check would refuse. */
    CasePage search(CaseScope scope, CaseQuery query, java.time.LocalDate today);

    DischargeCase create(NewCase request, UUID actorId);

    DischargeCase update(UUID id, int expectedVersion, CaseChanges changes, List<String> changedFields,
            UUID actorId);

    DischargeCase changeStatus(UUID id, int expectedVersion, CaseStatus from, CaseStatus to, UUID actorId);

    List<CaseEvent> events(UUID caseId);

    /** Active accounts with an active membership of {@code role} in the unit, by display name. */
    List<CaseMember> activeMembers(UUID careUnitId, Role role);
}
