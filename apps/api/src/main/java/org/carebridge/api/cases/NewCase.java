package org.carebridge.api.cases;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A validated request to open a case for a new synthetic patient.
 *
 * @param patientName already ends with " (DEMO)"
 * @param careTeam    the creating coordinator first, then an optional reviewer
 */
public record NewCase(
        UUID careUnitId,
        String patientName,
        String ageBand,
        LocalDate dischargeDate,
        LocalDate followUpDue,
        String summary,
        List<CaseMember> careTeam) {

    public NewCase {
        careTeam = List.copyOf(careTeam);
    }
}
