package org.carebridge.api.cases;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The editable details of a case, after validation; unchanged fields keep their current value. */
public record CaseChanges(LocalDate dischargeDate, LocalDate followUpDue, String summary) {

    /** Names of the fields that differ from {@code current}, in a stable order. */
    public List<String> changedFrom(DischargeCase current) {
        List<String> changed = new ArrayList<>();
        if (!dischargeDate.equals(current.dischargeDate())) {
            changed.add("dischargeDate");
        }
        if (!followUpDue.equals(current.followUpDue())) {
            changed.add("followUpDue");
        }
        if (!summary.equals(current.summary())) {
            changed.add("summary");
        }
        return changed;
    }
}
