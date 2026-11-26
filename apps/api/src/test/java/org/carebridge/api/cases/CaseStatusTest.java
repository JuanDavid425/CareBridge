package org.carebridge.api.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class CaseStatusTest {

    @ParameterizedTest(name = "{0} -> {1} needs {2}")
    @CsvSource({
        "OPEN, PLANNING, case.update",
        "PLANNING, REVIEW, case.update",
        "REVIEW, PLANNING, plan.publish",
        "REVIEW, ACTIVE, plan.publish",
        "ACTIVE, CLOSED, case.update",
    })
    void legalMovesNameTheirPermission(CaseStatus from, CaseStatus to, String permission) {
        assertThat(from.permissionTo(to)).contains(permission);
    }

    @ParameterizedTest(name = "{0} -> {1} is not part of the lifecycle")
    @CsvSource({
        "OPEN, ACTIVE", "OPEN, REVIEW", "OPEN, CLOSED", "OPEN, OPEN",
        "PLANNING, ACTIVE", "PLANNING, OPEN", "PLANNING, CLOSED",
        "REVIEW, CLOSED", "REVIEW, OPEN",
        "ACTIVE, PLANNING", "ACTIVE, REVIEW",
        "CLOSED, OPEN", "CLOSED, ACTIVE", "CLOSED, CLOSED",
    })
    void everythingElseIsRejected(CaseStatus from, CaseStatus to) {
        assertThat(from.permissionTo(to)).isEmpty();
    }

    @Test
    void nextListsReachableStatusesInLifecycleOrder() {
        assertThat(CaseStatus.REVIEW.next()).containsExactly(CaseStatus.PLANNING, CaseStatus.ACTIVE);
        assertThat(CaseStatus.CLOSED.next()).isEmpty();
    }

    @Test
    void wireValuesAreLowerCase() {
        assertThat(CaseStatus.parse("planning")).contains(CaseStatus.PLANNING);
        assertThat(CaseStatus.parse("PLANNING")).isEmpty();
        assertThat(CaseStatus.parse("archived")).isEmpty();
        assertThat(CaseStatus.parse(null)).isEmpty();
        assertThatThrownBy(() -> CaseStatus.fromCode("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
