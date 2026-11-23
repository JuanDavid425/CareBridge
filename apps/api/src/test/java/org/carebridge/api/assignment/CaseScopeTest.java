package org.carebridge.api.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseScopeTest {

    private final UUID account = UUID.randomUUID();
    private final UUID unitA = UUID.randomUUID();
    private final UUID unitB = UUID.randomUUID();

    @Test
    void careTeamScopeChecksUnitBeforeAssignment() {
        CaseScope scope = CaseScope.careTeam(account, Set.of(unitA));

        assertThat(scope.denialFor(new CaseRef(UUID.randomUUID(), unitA, Set.of(account), Set.of()))).isEmpty();
        assertThat(scope.denialFor(new CaseRef(UUID.randomUUID(), unitA, Set.of(), Set.of())))
                .contains(CaseAccessDenial.NOT_ASSIGNED);
        // Still assigned on a case that moved to another unit: the unit rule wins.
        assertThat(scope.denialFor(new CaseRef(UUID.randomUUID(), unitB, Set.of(account), Set.of())))
                .contains(CaseAccessDenial.OUTSIDE_UNIT);
    }

    @Test
    void careTeamWithoutUnitsIsEmpty() {
        assertThat(CaseScope.careTeam(account, Set.of()).isEmpty()).isTrue();
        assertThat(CaseScope.careTeam(account, Set.of(unitA)).isEmpty()).isFalse();
    }

    @Test
    void ownCaseScopeIgnoresUnitsAndCareTeam() {
        CaseScope scope = CaseScope.ownCase(account);

        assertThat(scope.careUnitIds()).isEmpty();
        assertThat(scope.denialFor(new CaseRef(UUID.randomUUID(), unitA, Set.of(), Set.of(account)))).isEmpty();
        assertThat(scope.denialFor(new CaseRef(UUID.randomUUID(), unitA, Set.of(account), Set.of())))
                .contains(CaseAccessDenial.NOT_LINKED);
        assertThat(scope.denialForUnit(unitA)).contains(CaseAccessDenial.NO_PERMISSION);
    }

    @Test
    void noneDeniesEverything() {
        CaseScope scope = CaseScope.none(account);

        assertThat(scope.isEmpty()).isTrue();
        assertThat(scope.denialFor(new CaseRef(UUID.randomUUID(), unitA, Set.of(account), Set.of(account))))
                .contains(CaseAccessDenial.NO_PERMISSION);
        assertThat(scope.denialForUnit(unitA)).contains(CaseAccessDenial.NO_PERMISSION);
    }
}
