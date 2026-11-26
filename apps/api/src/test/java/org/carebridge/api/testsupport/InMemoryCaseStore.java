package org.carebridge.api.testsupport;

import static org.carebridge.api.testsupport.InMemoryIdentityStore.idOf;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.carebridge.api.assignment.CaseRef;
import org.carebridge.api.assignment.CaseScope;
import org.carebridge.api.cases.CaseChanges;
import org.carebridge.api.cases.CaseConflictException;
import org.carebridge.api.cases.CaseEvent;
import org.carebridge.api.cases.CaseMember;
import org.carebridge.api.cases.CaseNotFoundException;
import org.carebridge.api.cases.CasePage;
import org.carebridge.api.cases.CaseQuery;
import org.carebridge.api.cases.CaseStatus;
import org.carebridge.api.cases.CaseStore;
import org.carebridge.api.cases.DischargeCase;
import org.carebridge.api.cases.NewCase;
import org.carebridge.api.cases.PatientInfo;
import org.carebridge.api.cases.StaleCaseVersionException;
import org.carebridge.api.identity.Account;
import org.carebridge.api.identity.Role;
import org.carebridge.api.identity.UnitMembership;

/**
 * {@link CaseStore} for web-layer tests with the same rules as {@code JdbcCaseStore}: scoped
 * search, version-checked writes and an event per write. Starts with the {@link TestCases}
 * fixtures; {@link #reset()} restores them.
 */
public class InMemoryCaseStore implements CaseStore {

    private final InMemoryIdentityStore identities;
    private final Clock clock;
    private final Map<UUID, DischargeCase> cases = new LinkedHashMap<>();
    private final List<CaseEvent> events = new ArrayList<>();
    private final AtomicInteger nextReference = new AtomicInteger(1001);
    private final AtomicLong nextEvent = new AtomicLong(1);

    public InMemoryCaseStore(InMemoryIdentityStore identities, Clock clock) {
        this.identities = identities;
        this.clock = clock;
        reset();
    }

    /** Back to the three fixture cases, each at version 1 with a {@code seeded} event. */
    public synchronized void reset() {
        cases.clear();
        events.clear();
        nextReference.set(1001);
        nextEvent.set(1);
        LocalDate today = LocalDate.now(clock);
        fixture(TestCases.CASE_008, "DEMO-CASE-008", "Tomás Alvarez (DEMO)", CaseStatus.OPEN, today.plusDays(2));
        fixture(TestCases.CASE_011, "DEMO-CASE-011", "Lena Brandt (DEMO)", CaseStatus.PLANNING, today.minusDays(9));
        fixture(TestCases.CASE_021, "DEMO-CASE-021", "Iris Moreno (DEMO)", CaseStatus.REVIEW, today.minusDays(3));
    }

    private void fixture(CaseRef ref, String reference, String patientName, CaseStatus status, LocalDate discharge) {
        UnitMembership unit = unitFor(ref.careUnitId());
        List<CaseMember> team = ref.careTeam().stream()
                .map(id -> identities.findById(id).orElseThrow())
                .sorted(Comparator.comparing((Account a) -> a.role() == Role.COORDINATOR ? 0 : 1)
                        .thenComparing(Account::displayName))
                .map(a -> new CaseMember(a.id(), a.displayName(), a.role()))
                .toList();
        Instant now = clock.instant();
        DischargeCase c = new DischargeCase(ref.caseId(), reference, ref.careUnitId(), unit.unitCode(),
                unit.unitName(), new PatientInfo(UUID.nameUUIDFromBytes(patientName.getBytes()),
                        reference.replace("CASE", "PT"), patientName, "60-69"),
                status, discharge, discharge.plusDays(7), "", 1, team, ref.portalAccounts(), now, now);
        cases.put(c.id(), c);
        event(c.id(), null, CaseEvent.SEEDED, null, null, List.of(), 1);
    }

    private static UnitMembership unitFor(UUID careUnitId) {
        for (UnitMembership m : List.of(InMemoryIdentityStore.UNIT_01_COORDINATOR,
                InMemoryIdentityStore.UNIT_02_COORDINATOR)) {
            if (m.careUnitId().equals(careUnitId)) {
                return m;
            }
        }
        throw new IllegalArgumentException("unknown test unit");
    }

    @Override
    public synchronized Optional<DischargeCase> findById(UUID id) {
        return Optional.ofNullable(cases.get(id));
    }

    @Override
    public synchronized CasePage search(CaseScope scope, CaseQuery query, LocalDate today) {
        if (scope.isEmpty()) {
            return CasePage.empty(query);
        }
        List<DischargeCase> matching = cases.values().stream()
                .filter(c -> scope.denialFor(c.ref()).isEmpty())
                .filter(c -> query.statuses().isEmpty() || query.statuses().contains(c.status()))
                .filter(c -> query.search() == null
                        || c.reference().toLowerCase(Locale.ROOT).contains(query.searchKey())
                        || c.patient().displayName().toLowerCase(Locale.ROOT).contains(query.searchKey()))
                .filter(c -> query.due() == null
                        || (!c.status().isClosed() && query.due().matches(c.followUpDue(), today)))
                .sorted(Comparator.comparing(DischargeCase::followUpDue).thenComparing(DischargeCase::reference))
                .toList();
        List<DischargeCase> page = matching.stream().skip(query.offset()).limit(query.size()).toList();
        return new CasePage(page, query.page(), query.size(), matching.size());
    }

    @Override
    public synchronized DischargeCase create(NewCase request, UUID actorId) {
        UnitMembership unit = unitFor(request.careUnitId());
        int number = nextReference.getAndIncrement();
        Instant now = clock.instant();
        DischargeCase c = new DischargeCase(UUID.randomUUID(), "DEMO-CASE-" + number, request.careUnitId(),
                unit.unitCode(), unit.unitName(),
                new PatientInfo(UUID.randomUUID(), "DEMO-PT-" + number, request.patientName(), request.ageBand()),
                CaseStatus.OPEN, request.dischargeDate(), request.followUpDue(), request.summary(), 1,
                request.careTeam(), Set.of(), now, now);
        cases.put(c.id(), c);
        event(c.id(), actorId, CaseEvent.CREATED, null, null, List.of(), 1);
        return c;
    }

    @Override
    public synchronized DischargeCase update(UUID id, int expectedVersion, CaseChanges changes,
            List<String> changedFields, UUID actorId) {
        DischargeCase c = current(id, expectedVersion);
        if (c.status().isClosed()) {
            throw new CaseConflictException(CaseConflictException.CASE_CLOSED, "Closed cases cannot be edited.");
        }
        DischargeCase updated = new DischargeCase(c.id(), c.reference(), c.careUnitId(), c.careUnitCode(),
                c.careUnitName(), c.patient(), c.status(), changes.dischargeDate(), changes.followUpDue(),
                changes.summary(), c.version() + 1, c.careTeam(), c.portalAccounts(), c.createdAt(), clock.instant());
        cases.put(id, updated);
        event(id, actorId, CaseEvent.UPDATED, null, null, changedFields, updated.version());
        return updated;
    }

    @Override
    public synchronized DischargeCase changeStatus(UUID id, int expectedVersion, CaseStatus from, CaseStatus to,
            UUID actorId) {
        DischargeCase c = current(id, expectedVersion);
        if (c.status() != from) {
            throw new StaleCaseVersionException(c.version());
        }
        DischargeCase updated = new DischargeCase(c.id(), c.reference(), c.careUnitId(), c.careUnitCode(),
                c.careUnitName(), c.patient(), to, c.dischargeDate(), c.followUpDue(), c.summary(),
                c.version() + 1, c.careTeam(), c.portalAccounts(), c.createdAt(), clock.instant());
        cases.put(id, updated);
        event(id, actorId, CaseEvent.STATUS_CHANGED, from, to, List.of(), updated.version());
        return updated;
    }

    @Override
    public synchronized List<CaseEvent> events(UUID caseId) {
        return events.stream().filter(e -> e.caseId().equals(caseId)).toList();
    }

    @Override
    public List<CaseMember> activeMembers(UUID careUnitId, Role role) {
        return List.of("maya.coordinator", "okafor.reviewer", "chen.coordinator", "leaving.coordinator").stream()
                .map(handle -> identities.findById(idOf(handle)).orElseThrow())
                .filter(Account::active)
                .filter(a -> a.role() == role)
                .filter(a -> a.memberships().stream().anyMatch(m -> m.careUnitId().equals(careUnitId)))
                .sorted(Comparator.comparing(Account::displayName))
                .map(a -> new CaseMember(a.id(), a.displayName(), a.role()))
                .toList();
    }

    private DischargeCase current(UUID id, int expectedVersion) {
        DischargeCase c = cases.get(id);
        if (c == null) {
            throw new CaseNotFoundException();
        }
        if (c.version() != expectedVersion) {
            throw new StaleCaseVersionException(c.version());
        }
        return c;
    }

    private void event(UUID caseId, UUID actorId, String type, CaseStatus from, CaseStatus to,
            List<String> fields, int version) {
        String actorName = actorId == null ? null : identities.findById(actorId).map(Account::displayName).orElse(null);
        events.add(new CaseEvent(nextEvent.getAndIncrement(), caseId, actorId, actorName, type, from, to, fields,
                version, clock.instant()));
    }
}
