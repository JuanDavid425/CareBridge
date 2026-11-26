package org.carebridge.api.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.carebridge.api.assignment.CaseRef;

/** A discharge case as stored, with its care team and linked portal accounts. */
public record DischargeCase(
        UUID id,
        String reference,
        UUID careUnitId,
        String careUnitCode,
        String careUnitName,
        PatientInfo patient,
        CaseStatus status,
        LocalDate dischargeDate,
        LocalDate followUpDue,
        String summary,
        int version,
        List<CaseMember> careTeam,
        Set<UUID> portalAccounts,
        Instant createdAt,
        Instant updatedAt) {

    public DischargeCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(careUnitId, "careUnitId");
        Objects.requireNonNull(status, "status");
        careTeam = List.copyOf(careTeam);
        portalAccounts = Set.copyOf(portalAccounts);
    }

    /** The facts access checks need. */
    public CaseRef ref() {
        return new CaseRef(id, careUnitId,
                careTeam.stream().map(CaseMember::userId).collect(Collectors.toSet()), portalAccounts);
    }

    public boolean isOverdue(LocalDate today) {
        return !status.isClosed() && followUpDue.isBefore(today);
    }
}
