package org.carebridge.api.cases;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.carebridge.api.security.CareBridgeUser;
import org.carebridge.api.web.InvalidParameterException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Synthetic discharge case registry. Lists only ever contain cases the caller is assigned to;
 * single-case calls answer 404 for unknown ids and the shared 403 for cases outside the caller's
 * scope. Every response for one case carries its version as an ETag, and every write needs it back
 * in If-Match.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Cases", description = "Synthetic discharge case registry")
public class CaseController {

    private final CaseService cases;

    public CaseController(CaseService cases) {
        this.cases = cases;
    }

    // ---- views -------------------------------------------------------------------------------

    public record UnitRef(UUID id, @Schema(example = "DEMO-UNIT-01") String code, String name) {
    }

    @Schema(description = "Synthetic patient; the name always ends with (DEMO)")
    public record PatientView(@Schema(example = "DEMO-PT-008") String reference,
            @Schema(example = "Tomás Alvarez (DEMO)") String displayName,
            @Schema(example = "60-69") String ageBand) {

        static PatientView of(PatientInfo patient) {
            return new PatientView(patient.reference(), patient.displayName(), patient.ageBand());
        }
    }

    public record MemberView(UUID id, String displayName,
            @Schema(allowableValues = {"coordinator", "reviewer"}) String role) {

        static MemberView of(CaseMember member) {
            return new MemberView(member.userId(), member.displayName(), member.role().code());
        }
    }

    @Schema(description = "A case in a list")
    public record CaseListItem(
            UUID id,
            @Schema(example = "DEMO-CASE-008") String reference,
            UnitRef careUnit,
            PatientView patient,
            @Schema(allowableValues = {"open", "planning", "review", "active", "closed"}) String status,
            LocalDate dischargeDate,
            LocalDate followUpDue,
            @Schema(description = "Follow-up date passed and the case is not closed") boolean overdue,
            int version) {
    }

    @Schema(description = "One page of cases, ordered by follow-up due date")
    public record CaseListPage(List<CaseListItem> items, int page, int size, long totalItems, int totalPages) {
    }

    @Schema(description = "A case with its care team and what the caller may do next")
    public record CaseView(
            UUID id,
            @Schema(example = "DEMO-CASE-008") String reference,
            UnitRef careUnit,
            PatientView patient,
            @Schema(allowableValues = {"open", "planning", "review", "active", "closed"}) String status,
            LocalDate dischargeDate,
            LocalDate followUpDue,
            boolean overdue,
            @Schema(description = "Free-text synthetic summary, at most 2000 characters") String summary,
            @Schema(description = "Also sent as the ETag header, e.g. \"v3\"") int version,
            List<MemberView> careTeam,
            @ArraySchema(schema = @Schema(allowableValues = {"open", "planning", "review", "active", "closed"}))
            List<String> allowedTransitions,
            @Schema(description = "The caller may edit dates and summary") boolean canEdit,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CaseEventView(long id, String type, String actor, String fromStatus, String toStatus,
            List<String> changedFields, int version, Instant occurredAt) {

        static CaseEventView of(CaseEvent event) {
            return new CaseEventView(event.id(), event.type(),
                    event.actorId() == null ? "Demo seed" : event.actorName(),
                    event.fromStatus() == null ? null : event.fromStatus().code(),
                    event.toStatus() == null ? null : event.toStatus().code(),
                    event.changedFields(), event.version(), event.occurredAt());
        }
    }

    // ---- requests ----------------------------------------------------------------------------

    public record CreateCaseRequest(
            @NotNull UUID careUnitId,
            @NotBlank @Size(max = 110) @Schema(example = "Ana Ruiz", description = "Invented name; (DEMO) is appended")
            String patientName,
            @NotBlank @Schema(example = "60-69", allowableValues = {"18-29", "30-39", "40-49", "50-59", "60-69", "70-79", "80+"})
            String ageBand,
            @NotNull LocalDate dischargeDate,
            @NotNull LocalDate followUpDue,
            @Size(max = 2000) String summary,
            @Schema(description = "Optional reviewer from the same care unit") UUID reviewerId) {
    }

    @Schema(description = "Fields to change; omitted fields keep their value")
    public record UpdateCaseRequest(LocalDate dischargeDate, LocalDate followUpDue, @Size(max = 2000) String summary) {
    }

    public record StatusChangeRequest(
            @NotBlank @Schema(example = "planning", allowableValues = {"open", "planning", "review", "active", "closed"})
            String status) {
    }

    // ---- endpoints ---------------------------------------------------------------------------

    @GetMapping("/cases")
    @Operation(summary = "Cases assigned to the caller, with search, status and follow-up due filters")
    public CaseListPage list(
            @RequestParam(required = false) @Parameter(description = "Case reference or patient name") String search,
            @RequestParam(required = false) @Parameter(description = "Repeat for several statuses") List<String> status,
            @RequestParam(required = false) @Parameter(schema = @Schema(allowableValues = {"overdue", "today", "week"}))
            String due,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "" + CaseQuery.DEFAULT_SIZE) String size,
            @AuthenticationPrincipal CareBridgeUser user) {
        CaseQuery query = parseQuery(search, status, due, page, size);
        CasePage result = cases.list(user, query);
        LocalDate today = cases.today();
        return new CaseListPage(result.items().stream().map(c -> listItem(c, today)).toList(),
                result.page(), result.size(), result.totalItems(), result.totalPages());
    }

    @PostMapping("/cases")
    @Operation(summary = "Open a case for a new synthetic patient; the caller joins its care team")
    public ResponseEntity<CaseView> create(@Valid @RequestBody CreateCaseRequest request,
            @AuthenticationPrincipal CareBridgeUser user) {
        DischargeCase created = cases.create(user, new CaseService.CreateCommand(request.careUnitId(),
                request.patientName(), request.ageBand(), request.dischargeDate(), request.followUpDue(),
                request.summary(), request.reviewerId()));
        return ResponseEntity.created(URI.create("/api/v1/cases/" + created.id()))
                .eTag(CaseETags.of(created.version()))
                .body(view(created, user));
    }

    @GetMapping("/cases/{caseId}")
    @Operation(summary = "One case; supports If-None-Match")
    public ResponseEntity<CaseView> get(@PathVariable UUID caseId,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @AuthenticationPrincipal CareBridgeUser user) {
        DischargeCase found = cases.get(user, caseId);
        if (CaseETags.parse(ifNoneMatch).orElse(-1) == found.version()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(CaseETags.of(found.version())).build();
        }
        return ResponseEntity.ok().eTag(CaseETags.of(found.version())).body(view(found, user));
    }

    @PatchMapping("/cases/{caseId}")
    @Operation(summary = "Edit discharge date, follow-up due date or summary; requires If-Match")
    public ResponseEntity<CaseView> update(@PathVariable UUID caseId,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateCaseRequest request,
            @AuthenticationPrincipal CareBridgeUser user) {
        DischargeCase updated = cases.update(user, caseId, ifMatch,
                new CaseService.UpdateCommand(request.dischargeDate(), request.followUpDue(), request.summary()));
        return ResponseEntity.ok().eTag(CaseETags.of(updated.version())).body(view(updated, user));
    }

    @PostMapping("/cases/{caseId}/status")
    @Operation(summary = "Move a case to its next status; requires If-Match")
    public ResponseEntity<CaseView> changeStatus(@PathVariable UUID caseId,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal CareBridgeUser user) {
        DischargeCase changed = cases.changeStatus(user, caseId, ifMatch, request.status());
        return ResponseEntity.ok().eTag(CaseETags.of(changed.version())).body(view(changed, user));
    }

    @GetMapping("/cases/{caseId}/events")
    @Operation(summary = "Append-only history of the case (field names only, no values)")
    public List<CaseEventView> events(@PathVariable UUID caseId, @AuthenticationPrincipal CareBridgeUser user) {
        return cases.history(user, caseId).stream().map(CaseEventView::of).toList();
    }

    @GetMapping("/care-units/{careUnitId}/reviewers")
    @Operation(summary = "Reviewers who can join a new case in the unit (callers who may create cases there)")
    public List<MemberView> reviewers(@PathVariable UUID careUnitId, @AuthenticationPrincipal CareBridgeUser user) {
        return cases.eligibleReviewers(user, careUnitId).stream().map(MemberView::of).toList();
    }

    // ---- mapping -----------------------------------------------------------------------------

    static CaseQuery parseQuery(String search, List<String> statusCodes, String due, String page, String size) {
        Map<String, String> problems = new TreeMap<>();
        Set<CaseStatus> statuses = EnumSet.noneOf(CaseStatus.class);
        for (String code : statusCodes == null ? List.<String>of() : statusCodes) {
            for (String part : code.split(",")) {
                if (part.isBlank()) {
                    continue;
                }
                CaseStatus.parse(part).ifPresentOrElse(statuses::add,
                        () -> problems.put("status", "Use open, planning, review, active or closed."));
            }
        }
        DueFilter dueFilter = null;
        if (due != null && !due.isBlank()) {
            dueFilter = DueFilter.parse(due).orElse(null);
            if (dueFilter == null) {
                problems.put("due", "Use overdue, today or week.");
            }
        }
        int pageNumber = parseInt(page, 0, 100_000, "page", problems);
        int pageSize = parseInt(size, 1, CaseQuery.MAX_SIZE, "size", problems);
        if (search != null && search.strip().length() > CaseQuery.MAX_SEARCH) {
            problems.put("search", "Use at most " + CaseQuery.MAX_SEARCH + " characters.");
        }
        if (!problems.isEmpty()) {
            throw new InvalidParameterException(problems);
        }
        return new CaseQuery(search, statuses, dueFilter, pageNumber, pageSize);
    }

    private static int parseInt(String raw, int min, int max, String name, Map<String, String> problems) {
        try {
            int value = Integer.parseInt(raw.strip());
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        problems.put(name, "Use a whole number from " + min + " to " + max + ".");
        return min;
    }

    private static UnitRef unit(DischargeCase c) {
        return new UnitRef(c.careUnitId(), c.careUnitCode(), c.careUnitName());
    }

    private static CaseListItem listItem(DischargeCase c, LocalDate today) {
        return new CaseListItem(c.id(), c.reference(), unit(c), PatientView.of(c.patient()), c.status().code(),
                c.dischargeDate(), c.followUpDue(), c.isOverdue(today), c.version());
    }

    private CaseView view(DischargeCase c, CareBridgeUser user) {
        return new CaseView(c.id(), c.reference(), unit(c), PatientView.of(c.patient()), c.status().code(),
                c.dischargeDate(), c.followUpDue(), c.isOverdue(cases.today()), c.summary(), c.version(),
                c.careTeam().stream().map(MemberView::of).toList(),
                cases.allowedTransitions(user, c).stream().map(CaseStatus::code).toList(),
                cases.canEdit(user, c), c.createdAt(), c.updatedAt());
    }
}
