package org.carebridge.api.cases;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.carebridge.api.assignment.CaseAccessDenial;
import org.carebridge.api.assignment.CaseAccessDeniedException;
import org.carebridge.api.assignment.CaseAuthorizationService;
import org.carebridge.api.assignment.CaseScope;
import org.carebridge.api.identity.Role;
import org.carebridge.api.security.CareBridgeUser;
import org.springframework.stereotype.Service;

/**
 * Case registry use cases. Every method checks access through {@link CaseAuthorizationService}
 * before it reads or writes anything else, so an account outside a case learns nothing about it
 * beyond the shared 403.
 */
@Service
public class CaseService {

    public static final String READ = "case.read";
    public static final String CREATE = "case.create";
    public static final String UPDATE = CaseStatus.UPDATE;
    public static final String HISTORY = "audit.read";

    /** How far back and ahead a discharge date may be set. */
    static final int DISCHARGE_PAST_DAYS = 30;
    static final int DISCHARGE_FUTURE_DAYS = 60;
    /** Longest gap between discharge and follow-up (mirrors the V4 CHECK). */
    static final int FOLLOW_UP_WINDOW_DAYS = 90;
    static final int MAX_NAME = 100;
    static final int MAX_SUMMARY = 2000;

    private static final String DEMO_SUFFIX = " (DEMO)";
    private static final Pattern DEMO_SUFFIX_ANY_CASE = Pattern.compile("\\s*\\(demo\\)\\s*$", Pattern.CASE_INSENSITIVE);
    /** Letters, spaces, apostrophes, hyphens and dots: no digits, so no record numbers or dates. */
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L} .'\\-]*$");

    private final CaseStore store;
    private final CaseAuthorizationService authorization;
    private final Clock clock;

    public CaseService(CaseStore store, CaseAuthorizationService authorization, Clock clock) {
        this.store = store;
        this.authorization = authorization;
        this.clock = clock;
    }

    public record CreateCommand(UUID careUnitId, String patientName, String ageBand, LocalDate dischargeDate,
            LocalDate followUpDue, String summary, UUID reviewerId) {
    }

    /** Null fields keep their current value. */
    public record UpdateCommand(LocalDate dischargeDate, LocalDate followUpDue, String summary) {
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public CasePage list(CareBridgeUser user, CaseQuery query) {
        CaseScope scope = authorization.scopeFor(user.id(), READ);
        if (scope.kind() == CaseScope.Kind.NONE) {
            throw new CaseAccessDeniedException(CaseAccessDenial.NO_PERMISSION, READ, null);
        }
        if (scope.isEmpty()) {
            return CasePage.empty(query);
        }
        return store.search(scope, query, today());
    }

    public DischargeCase get(CareBridgeUser user, UUID caseId) {
        DischargeCase found = find(caseId);
        authorization.require(user, found.ref(), READ);
        return found;
    }

    public List<CaseEvent> history(CareBridgeUser user, UUID caseId) {
        DischargeCase found = get(user, caseId);
        authorization.require(user, found.ref(), HISTORY);
        return store.events(caseId);
    }

    /** Reviewers who can be put on a new case in the unit; only for accounts that may create there. */
    public List<CaseMember> eligibleReviewers(CareBridgeUser user, UUID careUnitId) {
        authorization.requireInUnit(user, careUnitId, CREATE);
        return store.activeMembers(careUnitId, Role.REVIEWER);
    }

    public DischargeCase create(CareBridgeUser user, CreateCommand command) {
        authorization.requireInUnit(user, command.careUnitId(), CREATE);
        Map<String, String> problems = new TreeMap<>();

        String patientName = normalizePatientName(command.patientName());
        String bare = patientName.substring(0, patientName.length() - DEMO_SUFFIX.length());
        if (bare.isEmpty()) {
            problems.put("patientName", "Enter an invented name for the synthetic patient.");
        } else if (bare.length() > MAX_NAME) {
            problems.put("patientName", "Use at most " + MAX_NAME + " characters.");
        } else if (!NAME.matcher(bare).matches()) {
            problems.put("patientName", "Use letters, spaces, apostrophes and hyphens only; no numbers.");
        }
        if (!AgeBands.isValid(command.ageBand())) {
            problems.put("ageBand", "Choose an age band.");
        }
        checkDischargeDate(command.dischargeDate(), problems);
        checkFollowUp(command.dischargeDate(), command.followUpDue(), problems);
        String summary = command.summary() == null ? "" : command.summary().strip();
        if (summary.length() > MAX_SUMMARY) {
            problems.put("summary", "Use at most " + MAX_SUMMARY + " characters.");
        }

        CaseMember coordinator = store.activeMembers(command.careUnitId(), Role.COORDINATOR).stream()
                .filter(member -> member.userId().equals(user.id()))
                .findFirst()
                // requireInUnit passed, so only a coordinator who left the unit a moment ago lands here.
                .orElseThrow(() -> new CaseAccessDeniedException(CaseAccessDenial.OUTSIDE_UNIT, CREATE,
                        command.careUnitId()));
        Optional<CaseMember> reviewer = Optional.empty();
        if (command.reviewerId() != null) {
            reviewer = store.activeMembers(command.careUnitId(), Role.REVIEWER).stream()
                    .filter(member -> member.userId().equals(command.reviewerId()))
                    .findFirst();
            if (reviewer.isEmpty()) {
                problems.put("reviewerId", "Choose a reviewer from this care unit.");
            }
        }
        if (!problems.isEmpty()) {
            throw new CaseValidationException(problems);
        }

        List<CaseMember> team = reviewer.map(r -> List.of(coordinator, r)).orElseGet(() -> List.of(coordinator));
        return store.create(new NewCase(command.careUnitId(), patientName, command.ageBand(),
                command.dischargeDate(), command.followUpDue(), summary, team), user.id());
    }

    public DischargeCase update(CareBridgeUser user, UUID caseId, String ifMatch, UpdateCommand command) {
        DischargeCase current = get(user, caseId);
        int expected = CaseETags.require(ifMatch);
        authorization.require(user, current.ref(), UPDATE);
        if (current.status().isClosed()) {
            throw new CaseConflictException(CaseConflictException.CASE_CLOSED, "Closed cases cannot be edited.");
        }

        CaseChanges changes = new CaseChanges(
                command.dischargeDate() != null ? command.dischargeDate() : current.dischargeDate(),
                command.followUpDue() != null ? command.followUpDue() : current.followUpDue(),
                command.summary() != null ? command.summary().strip() : current.summary());
        List<String> changed = changes.changedFrom(current);

        Map<String, String> problems = new TreeMap<>();
        if (changed.contains("dischargeDate")) {
            checkDischargeDate(changes.dischargeDate(), problems);
        }
        if (changed.contains("dischargeDate") || changed.contains("followUpDue")) {
            checkFollowUp(changes.dischargeDate(), changes.followUpDue(), problems);
        }
        if (changes.summary().length() > MAX_SUMMARY) {
            problems.put("summary", "Use at most " + MAX_SUMMARY + " characters.");
        }
        if (!problems.isEmpty()) {
            throw new CaseValidationException(problems);
        }
        if (expected != current.version()) {
            throw new StaleCaseVersionException(current.version());
        }
        if (changed.isEmpty()) {
            return current;
        }
        return store.update(caseId, expected, changes, changed, user.id());
    }

    public DischargeCase changeStatus(CareBridgeUser user, UUID caseId, String ifMatch, String targetCode) {
        DischargeCase current = get(user, caseId);
        int expected = CaseETags.require(ifMatch);
        CaseStatus target = CaseStatus.parse(targetCode)
                .orElseThrow(() -> new CaseValidationException(Map.of("status", "Choose a case status.")));
        String permission = current.status().permissionTo(target)
                .orElseThrow(() -> new CaseConflictException(CaseConflictException.INVALID_TRANSITION,
                        "A case in " + current.status().code() + " cannot move to " + target.code() + "."));
        authorization.require(user, current.ref(), permission);
        if (expected != current.version()) {
            throw new StaleCaseVersionException(current.version());
        }
        return store.changeStatus(caseId, expected, current.status(), target, user.id());
    }

    /** The moves this account may make now, for the UI; the server checks again on submit. */
    public List<CaseStatus> allowedTransitions(CareBridgeUser user, DischargeCase found) {
        return found.status().next().stream()
                .filter(target -> authorization.check(user.id(), found.ref(),
                        found.status().permissionTo(target).orElseThrow()).isEmpty())
                .toList();
    }

    public boolean canEdit(CareBridgeUser user, DischargeCase found) {
        return !found.status().isClosed() && authorization.check(user.id(), found.ref(), UPDATE).isEmpty();
    }

    /** "Ana Ruiz" and "Ana Ruiz (demo)" both become "Ana Ruiz (DEMO)"; inner whitespace collapses. */
    static String normalizePatientName(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        name = DEMO_SUFFIX_ANY_CASE.matcher(name).replaceFirst("").strip();
        return name + DEMO_SUFFIX;
    }

    private void checkDischargeDate(LocalDate dischargeDate, Map<String, String> problems) {
        LocalDate today = today();
        if (dischargeDate == null) {
            problems.put("dischargeDate", "Enter the planned or actual discharge date.");
        } else if (dischargeDate.isBefore(today.minusDays(DISCHARGE_PAST_DAYS))
                || dischargeDate.isAfter(today.plusDays(DISCHARGE_FUTURE_DAYS))) {
            problems.put("dischargeDate", String.format(Locale.ROOT,
                    "Use a date from %d days ago to %d days ahead.", DISCHARGE_PAST_DAYS, DISCHARGE_FUTURE_DAYS));
        }
    }

    private static void checkFollowUp(LocalDate dischargeDate, LocalDate followUpDue, Map<String, String> problems) {
        if (followUpDue == null) {
            problems.put("followUpDue", "Enter the follow-up due date.");
        } else if (dischargeDate != null && followUpDue.isBefore(dischargeDate)) {
            problems.put("followUpDue", "The follow-up cannot be due before discharge.");
        } else if (dischargeDate != null && followUpDue.isAfter(dischargeDate.plusDays(FOLLOW_UP_WINDOW_DAYS))) {
            problems.put("followUpDue", "Use a date within " + FOLLOW_UP_WINDOW_DAYS + " days of discharge.");
        }
    }

    private DischargeCase find(UUID caseId) {
        return store.findById(caseId).orElseThrow(CaseNotFoundException::new);
    }
}
