package org.carebridge.api.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.carebridge.api.testsupport.InMemoryIdentityStore.idOf;
import static org.carebridge.api.testsupport.TestCases.CASE_008;
import static org.carebridge.api.testsupport.TestCases.CASE_011;
import static org.carebridge.api.testsupport.TestCases.CASE_021;
import static org.carebridge.api.testsupport.TestCases.UNIT_01;
import static org.carebridge.api.testsupport.TestCases.UNIT_02;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.carebridge.api.security.CareBridgeUser;
import org.carebridge.api.testsupport.InMemoryIdentityStore;
import org.carebridge.api.testsupport.TestCases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class CaseAuthorizationServiceTest {

    private InMemoryIdentityStore identities;
    private CaseAuthorizationService authorization;

    @BeforeEach
    void setUp() {
        identities = new InMemoryIdentityStore(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        authorization = new CaseAuthorizationService(identities);
    }

    private CareBridgeUser user(String handle) {
        return new CareBridgeUser(identities.findByHandle(handle).orElseThrow());
    }

    private Optional<CaseAccessDenial> check(String handle, CaseRef target, String permission) {
        return authorization.check(idOf(handle), target, permission);
    }

    @ParameterizedTest(name = "{0} may {2} on {1}")
    @CsvSource({
            "maya.coordinator,  DEMO-CASE-008, case.read",
            "maya.coordinator,  DEMO-CASE-008, task.assign",
            "okafor.reviewer,   DEMO-CASE-008, proposal.review",
            "chen.coordinator,  DEMO-CASE-021, case.read",
            "alvarez.patient,   DEMO-CASE-008, plan.read_published",
            "alvarez.caregiver, DEMO-CASE-008, plan.read_published"
    })
    void assignedAndLinkedAccountsAreAllowed(String handle, String caseRef, String permission) {
        CaseRef target = TestCases.find(TestCases.caseId(caseRef)).orElseThrow();

        assertThat(check(handle, target, permission)).isEmpty();
    }

    @Test
    void careTeamMemberOfAnotherUnitIsDenied() {
        assertThat(check("chen.coordinator", CASE_008, "case.read")).contains(CaseAccessDenial.OUTSIDE_UNIT);
        assertThat(check("maya.coordinator", CASE_021, "case.read")).contains(CaseAccessDenial.OUTSIDE_UNIT);
        assertThat(check("okafor.reviewer", CASE_021, "proposal.review")).contains(CaseAccessDenial.OUTSIDE_UNIT);
    }

    @Test
    void unitMembershipWithoutAssignmentIsDenied() {
        assertThat(check("maya.coordinator", CASE_011, "case.read")).contains(CaseAccessDenial.NOT_ASSIGNED);
        assertThat(check("okafor.reviewer", CASE_011, "proposal.review")).contains(CaseAccessDenial.NOT_ASSIGNED);
    }

    @Test
    void permissionOutsideTheRoleIsDeniedEvenWhenAssigned() {
        assertThat(check("okafor.reviewer", CASE_008, "task.assign")).contains(CaseAccessDenial.NO_PERMISSION);
        assertThat(check("maya.coordinator", CASE_008, "plan.publish")).contains(CaseAccessDenial.NO_PERMISSION);
        assertThat(check("maya.coordinator", CASE_008, "no.such_permission"))
                .contains(CaseAccessDenial.NO_PERMISSION);
    }

    @Test
    void portalAccountsOnlyReadPublishedPlansOfTheirOwnCase() {
        assertThat(check("alvarez.patient", CASE_008, "case.read")).contains(CaseAccessDenial.NO_PERMISSION);
        assertThat(check("alvarez.patient", CASE_021, "plan.read_published"))
                .contains(CaseAccessDenial.NOT_LINKED);
    }

    @Test
    void globalPermissionsNeverOpenCaseContent() {
        assertThat(check("lee.admin", CASE_008, "case.read")).contains(CaseAccessDenial.NO_PERMISSION);
        // audit.read is global for administrators: instance administration, not a case's timeline.
        assertThat(check("lee.admin", CASE_008, "audit.read")).contains(CaseAccessDenial.NO_PERMISSION);
    }

    @Test
    void deactivatedAccountsLoseAccessOnTheNextCheck() {
        CaseRef withLeaving = new CaseRef(UUID.randomUUID(), UNIT_01, Set.of(idOf("leaving.coordinator")), Set.of());
        assertThat(check("leaving.coordinator", withLeaving, "case.read")).isEmpty();

        identities.deactivate("leaving.coordinator");

        assertThat(check("leaving.coordinator", withLeaving, "case.read")).contains(CaseAccessDenial.NO_PERMISSION);
    }

    @Test
    void unknownAccountsHaveNoScope() {
        assertThat(authorization.scopeFor(UUID.randomUUID(), "case.read").isEmpty()).isTrue();
    }

    @Test
    void scopesMatchTheRoleMatrix() {
        assertThat(authorization.scopeFor(idOf("maya.coordinator"), "case.read"))
                .isEqualTo(CaseScope.careTeam(idOf("maya.coordinator"), Set.of(UNIT_01)));
        assertThat(authorization.scopeFor(idOf("alvarez.patient"), "plan.read_published"))
                .isEqualTo(CaseScope.ownCase(idOf("alvarez.patient")));
        assertThat(authorization.scopeFor(idOf("lee.admin"), "audit.read"))
                .isEqualTo(CaseScope.none(idOf("lee.admin")));
    }

    @Test
    void requireThrowsWithTheReason() {
        assertThatThrownBy(() -> authorization.require(user("maya.coordinator"), CASE_011, "case.read"))
                .isInstanceOfSatisfying(CaseAccessDeniedException.class, denied -> {
                    assertThat(denied.reason()).isEqualTo(CaseAccessDenial.NOT_ASSIGNED);
                    assertThat(denied.target()).isEqualTo(CASE_011.caseId());
                });
    }

    @Test
    void creatingCasesNeedsMembershipInThatUnit() {
        authorization.requireInUnit(user("maya.coordinator"), UNIT_01, "case.create");

        assertThatThrownBy(() -> authorization.requireInUnit(user("maya.coordinator"), UNIT_02, "case.create"))
                .isInstanceOf(CaseAccessDeniedException.class)
                .extracting(ex -> ((CaseAccessDeniedException) ex).reason())
                .isEqualTo(CaseAccessDenial.OUTSIDE_UNIT);
        assertThatThrownBy(() -> authorization.requireInUnit(user("okafor.reviewer"), UNIT_01, "case.create"))
                .isInstanceOf(CaseAccessDeniedException.class)
                .extracting(ex -> ((CaseAccessDeniedException) ex).reason())
                .isEqualTo(CaseAccessDenial.NO_PERMISSION);
    }
}
