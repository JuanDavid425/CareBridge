package org.carebridge.api.testsupport;

import static org.carebridge.api.testsupport.InMemoryIdentityStore.idOf;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.carebridge.api.assignment.CaseRef;

/** Synthetic cases for access tests, using the accounts in {@link InMemoryIdentityStore}. */
public final class TestCases {

    public static final UUID UNIT_01 = InMemoryIdentityStore.UNIT_01_COORDINATOR.careUnitId();
    public static final UUID UNIT_02 = InMemoryIdentityStore.UNIT_02_COORDINATOR.careUnitId();

    /** Unit 01; Maya and Okafor on the care team; the Alvarez portal accounts linked. */
    public static final CaseRef CASE_008 = new CaseRef(caseId("DEMO-CASE-008"), UNIT_01,
            Set.of(idOf("maya.coordinator"), idOf("okafor.reviewer")),
            Set.of(idOf("alvarez.patient"), idOf("alvarez.caregiver")));

    /** Unit 01 but nobody assigned yet: unit membership alone must not open it. */
    public static final CaseRef CASE_011 = new CaseRef(caseId("DEMO-CASE-011"), UNIT_01, Set.of(), Set.of());

    /** Unit 02; only Chen on the care team. */
    public static final CaseRef CASE_021 = new CaseRef(caseId("DEMO-CASE-021"), UNIT_02,
            Set.of(idOf("chen.coordinator")), Set.of());

    private static final Map<UUID, CaseRef> ALL = Map.of(
            CASE_008.caseId(), CASE_008,
            CASE_011.caseId(), CASE_011,
            CASE_021.caseId(), CASE_021);

    private TestCases() {
    }

    public static UUID caseId(String ref) {
        return UUID.nameUUIDFromBytes(ref.getBytes());
    }

    public static Optional<CaseRef> find(UUID caseId) {
        return Optional.ofNullable(ALL.get(caseId));
    }
}
