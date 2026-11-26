package org.carebridge.api.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.carebridge.api.testsupport.InMemoryCaseStore;
import org.carebridge.api.testsupport.InMemoryIdentityStore;
import org.carebridge.api.testsupport.TestCases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Case registry endpoints through the real filter chain, with the in-memory store and "today"
 * fixed at 2026-03-02. Fixtures: Case 008 (unit 01, Maya + Okafor, open, due 03-11), Case 011
 * (unit 01, nobody assigned) and Case 021 (unit 02, Chen, review, due 03-06).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseApiTest {

    private static final String CASE_008 = "/api/v1/cases/" + TestCases.CASE_008.caseId();
    private static final String CASE_021 = "/api/v1/cases/" + TestCases.CASE_021.caseId();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InMemoryCaseStore store;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void resetCases() {
        store.reset();
    }

    private MockHttpSession signIn(String handle) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handle\": \"%s\", \"password\": \"%s\"}"
                                .formatted(handle, InMemoryIdentityStore.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String newCase(String name, String discharge, String due, UUID reviewerId) {
        return """
                {"careUnitId": "%s", "patientName": "%s", "ageBand": "50-59",
                 "dischargeDate": "%s", "followUpDue": "%s", "summary": "Invented follow-up notes.",
                 "reviewerId": %s}"""
                .formatted(TestCases.UNIT_01, name, discharge, due,
                        reviewerId == null ? "null" : "\"" + reviewerId + "\"");
    }

    private JsonNode create(MockHttpSession session, String name, String discharge, String due) throws Exception {
        String body = mvc.perform(post("/api/v1/cases").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newCase(name, discharge, due, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    // ---- list --------------------------------------------------------------------------------

    @Test
    void listsOnlyCasesOnTheCallersCareTeam() throws Exception {
        mvc.perform(get("/api/v1/cases").session(signIn("maya.coordinator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].reference", contains("DEMO-CASE-008")))
                .andExpect(jsonPath("$.items[0].patient.displayName").value("Tomás Alvarez (DEMO)"))
                .andExpect(jsonPath("$.items[0].careUnit.code").value("DEMO-UNIT-01"))
                .andExpect(jsonPath("$.items[0].summary").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(get("/api/v1/cases").session(signIn("chen.coordinator")))
                .andExpect(jsonPath("$.items[*].reference", contains("DEMO-CASE-021")));
        mvc.perform(get("/api/v1/cases").session(signIn("okafor.reviewer")))
                .andExpect(jsonPath("$.items[*].reference", contains("DEMO-CASE-008")));
    }

    @Test
    void searchStatusDueAndPagingNarrowTheList() throws Exception {
        MockHttpSession maya = signIn("maya.coordinator");
        create(maya, "Ana Ruiz", "2026-02-20", "2026-02-27");   // overdue
        create(maya, "Bruno Keller", "2026-03-01", "2026-03-02"); // due today
        create(maya, "Carla Ruiz", "2026-03-05", "2026-04-20");   // later

        mvc.perform(get("/api/v1/cases").session(maya).param("search", "ruiz"))
                .andExpect(jsonPath("$.items[*].patient.displayName", contains("Ana Ruiz (DEMO)", "Carla Ruiz (DEMO)")));
        mvc.perform(get("/api/v1/cases").session(maya).param("search", "case-008"))
                .andExpect(jsonPath("$.items[*].reference", contains("DEMO-CASE-008")));
        mvc.perform(get("/api/v1/cases").session(maya).param("due", "overdue"))
                .andExpect(jsonPath("$.items[*].patient.displayName", contains("Ana Ruiz (DEMO)")))
                .andExpect(jsonPath("$.items[0].overdue").value(true));
        mvc.perform(get("/api/v1/cases").session(maya).param("due", "today"))
                .andExpect(jsonPath("$.items[*].patient.displayName", contains("Bruno Keller (DEMO)")));
        mvc.perform(get("/api/v1/cases").session(maya).param("due", "week"))
                .andExpect(jsonPath("$.items[*].reference", contains("DEMO-CASE-1002")));
        mvc.perform(get("/api/v1/cases").session(maya).param("status", "planning"))
                .andExpect(jsonPath("$.items", empty()));
        mvc.perform(get("/api/v1/cases").session(maya).param("status", "open,review"))
                .andExpect(jsonPath("$.totalItems").value(4));

        // Ordered by follow-up due date: Ana (02-27), Bruno (03-02), Case 008 (03-11), Carla (04-20).
        mvc.perform(get("/api/v1/cases").session(maya).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].reference", contains("DEMO-CASE-008", "DEMO-CASE-1003")))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/cases").session(maya).param("size", "2").param("page", "5"))
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalItems").value(4));
    }

    @Test
    void searchTextIsMatchedLiterally() throws Exception {
        mvc.perform(get("/api/v1/cases").session(signIn("maya.coordinator")).param("search", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void badListParametersAreReportedPerField() throws Exception {
        mvc.perform(get("/api/v1/cases").session(signIn("maya.coordinator"))
                        .param("status", "archived").param("due", "soon").param("size", "500").param("page", "x")
                        .param("search", "a".repeat(81)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_parameter"))
                .andExpect(jsonPath("$.error.fields.status").exists())
                .andExpect(jsonPath("$.error.fields.due").exists())
                .andExpect(jsonPath("$.error.fields.size").exists())
                .andExpect(jsonPath("$.error.fields.page").exists())
                .andExpect(jsonPath("$.error.fields.search").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lee.admin", "alvarez.patient", "alvarez.caregiver"})
    void accountsWithoutCaseAccessCannotList(String handle) throws Exception {
        mvc.perform(get("/api/v1/cases").session(signIn(handle)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/cases")).andExpect(status().isUnauthorized());
        mvc.perform(get(CASE_008)).andExpect(status().isUnauthorized());
    }

    // ---- detail ------------------------------------------------------------------------------

    @Test
    void detailCarriesTheVersionAsETagAndWhatTheCallerMayDo() throws Exception {
        mvc.perform(get(CASE_008).session(signIn("maya.coordinator")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"v1\""))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.followUpDue").value("2026-03-11"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.careTeam[*].role", contains("coordinator", "reviewer")))
                .andExpect(jsonPath("$.allowedTransitions", contains("planning")))
                .andExpect(jsonPath("$.canEdit").value(true));
        mvc.perform(get(CASE_008).session(signIn("okafor.reviewer")))
                .andExpect(jsonPath("$.allowedTransitions", empty()))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void ifNoneMatchWithTheCurrentVersionIsNotModified() throws Exception {
        mvc.perform(get(CASE_008).session(signIn("maya.coordinator")).header(HttpHeaders.IF_NONE_MATCH, "\"v1\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, "\"v1\""));
    }

    @Test
    void casesOutsideTheCareTeamAreForbiddenAndUnknownIdsNotFound() throws Exception {
        MockHttpSession maya = signIn("maya.coordinator");
        mvc.perform(get(CASE_021).session(maya))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"))
                .andExpect(jsonPath("$.patient").doesNotExist());
        mvc.perform(get("/api/v1/cases/" + TestCases.CASE_011.caseId()).session(maya))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID()).session(maya))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
        mvc.perform(get("/api/v1/cases/not-a-uuid").session(maya))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_parameter"));
    }

    // ---- create ------------------------------------------------------------------------------

    @Test
    void coordinatorsOpenCasesForNewSyntheticPatients() throws Exception {
        MockHttpSession maya = signIn("maya.coordinator");
        MvcResult result = mvc.perform(post("/api/v1/cases").session(maya).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newCase("  Ana   Ruiz ", "2026-03-04", "2026-03-11",
                                InMemoryIdentityStore.idOf("okafor.reviewer"))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"v1\""))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/cases/")))
                .andExpect(jsonPath("$.reference").value("DEMO-CASE-1001"))
                .andExpect(jsonPath("$.patient.reference").value("DEMO-PT-1001"))
                .andExpect(jsonPath("$.patient.displayName").value("Ana Ruiz (DEMO)"))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.careTeam[*].displayName",
                        contains("maya.coordinator (DEMO)", "okafor.reviewer (DEMO)")))
                .andReturn();
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);

        mvc.perform(get(location).session(maya)).andExpect(status().isOk());
        mvc.perform(get(location + "/events").session(maya))
                .andExpect(jsonPath("$[*].type", contains("created")))
                .andExpect(jsonPath("$[0].actor").value("maya.coordinator (DEMO)"));
        // Okafor was put on the care team, so the case shows up in his list too.
        mvc.perform(get("/api/v1/cases").session(signIn("okafor.reviewer")))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void createChecksFieldsAndCrossFieldRules() throws Exception {
        mvc.perform(post("/api/v1/cases").session(signIn("maya.coordinator")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newCase("MRN 4411", "2025-12-01", "2025-11-30",
                                InMemoryIdentityStore.idOf("chen.coordinator"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("validation_failed"))
                .andExpect(jsonPath("$.error.fields.patientName").exists())
                .andExpect(jsonPath("$.error.fields.dischargeDate").exists())
                .andExpect(jsonPath("$.error.fields.followUpDue").exists())
                .andExpect(jsonPath("$.error.fields.reviewerId").exists());
        mvc.perform(post("/api/v1/cases").session(signIn("maya.coordinator")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientName\": \"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fields.careUnitId").exists())
                .andExpect(jsonPath("$.error.fields.ageBand").exists());
    }

    @Test
    void onlyCoordinatorsOfTheUnitMayCreateThere() throws Exception {
        String body = newCase("Ana Ruiz", "2026-03-04", "2026-03-11", null);
        mvc.perform(post("/api/v1/cases").session(signIn("chen.coordinator")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cases").session(signIn("okafor.reviewer")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cases").session(signIn("maya.coordinator"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("csrf_token_invalid"));
    }

    @Test
    void reviewerPickerListsTheUnitsReviewers() throws Exception {
        mvc.perform(get("/api/v1/care-units/" + TestCases.UNIT_01 + "/reviewers").session(signIn("maya.coordinator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].displayName", contains("okafor.reviewer (DEMO)")));
        mvc.perform(get("/api/v1/care-units/" + TestCases.UNIT_02 + "/reviewers").session(signIn("maya.coordinator")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/care-units/" + TestCases.UNIT_01 + "/reviewers").session(signIn("okafor.reviewer")))
                .andExpect(status().isForbidden());
    }

    // ---- update and status -------------------------------------------------------------------

    @Test
    void editsNeedTheCurrentVersionInIfMatch() throws Exception {
        MockHttpSession maya = signIn("maya.coordinator");
        String change = "{\"followUpDue\": \"2026-03-12\", \"summary\": \"Call on Thursday.\"}";

        mvc.perform(patch(CASE_008).session(maya).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.error.code").value("precondition_required"));
        mvc.perform(patch(CASE_008).session(maya).with(csrf()).header(HttpHeaders.IF_MATCH, "\"v7\"")
                        .contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string(HttpHeaders.ETAG, "\"v1\""))
                .andExpect(jsonPath("$.error.code").value("stale_version"));
        mvc.perform(patch(CASE_008).session(maya).with(csrf()).header(HttpHeaders.IF_MATCH, "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"v2\""))
                .andExpect(jsonPath("$.followUpDue").value("2026-03-12"))
                .andExpect(jsonPath("$.summary").value("Call on Thursday."));
        // The same edit again with the old version is refused instead of overwriting.
        mvc.perform(patch(CASE_008).session(maya).with(csrf()).header(HttpHeaders.IF_MATCH, "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"summary\": \"Other text.\"}"))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(get(CASE_008 + "/events").session(maya))
                .andExpect(jsonPath("$[*].type", contains("seeded", "updated")))
                .andExpect(jsonPath("$[0].actor").value("Demo seed"))
                .andExpect(jsonPath("$[1].changedFields", contains("followUpDue", "summary")))
                .andExpect(jsonPath("$[1].version").value(2));
    }

    @Test
    void editsWithoutChangesKeepTheVersion() throws Exception {
        mvc.perform(patch(CASE_008).session(signIn("maya.coordinator")).with(csrf())
                        .header(HttpHeaders.IF_MATCH, "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"summary\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"v1\""));
        assertThat(store.events(TestCases.CASE_008.caseId())).hasSize(1);
    }

    @Test
    void reviewersCannotEditCaseDetails() throws Exception {
        mvc.perform(patch(CASE_008).session(signIn("okafor.reviewer")).with(csrf())
                        .header(HttpHeaders.IF_MATCH, "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"summary\": \"x\"}"))
                .andExpect(status().isForbidden());
    }

    private void move(MockHttpSession session, String path, int version, String to, int expectedStatus)
            throws Exception {
        mvc.perform(post(path + "/status").session(session).with(csrf())
                        .header(HttpHeaders.IF_MATCH, CaseETags.of(version))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + to + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void aCaseMovesThroughItsLifecycleWithTheRightRoles() throws Exception {
        MockHttpSession maya = signIn("maya.coordinator");
        MockHttpSession okafor = signIn("okafor.reviewer");

        move(maya, CASE_008, 1, "active", 409);   // shortcut
        move(okafor, CASE_008, 1, "planning", 403); // reviewers do not run intake
        move(maya, CASE_008, 1, "planning", 200);
        move(maya, CASE_008, 2, "review", 200);
        move(maya, CASE_008, 3, "active", 403);   // whoever plans does not publish
        mvc.perform(get(CASE_008).session(okafor))
                .andExpect(jsonPath("$.allowedTransitions", contains("planning", "active")));
        move(okafor, CASE_008, 3, "active", 200);
        move(maya, CASE_008, 4, "closed", 200);

        mvc.perform(get(CASE_008).session(maya))
                .andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.allowedTransitions", empty()))
                .andExpect(jsonPath("$.canEdit").value(false));
        mvc.perform(patch(CASE_008).session(maya).with(csrf()).header(HttpHeaders.IF_MATCH, "\"v5\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"summary\": \"late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("case_closed"));
        mvc.perform(get(CASE_008 + "/events").session(maya))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].toStatus", contains(null, "planning", "review", "active", "closed")));
    }

    @Test
    void transitionErrorsUseStableCodes() throws Exception {
        MockHttpSession maya = signIn("maya.coordinator");
        mvc.perform(post(CASE_008 + "/status").session(maya).with(csrf()).header(HttpHeaders.IF_MATCH, "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"review\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("invalid_transition"));
        mvc.perform(post(CASE_008 + "/status").session(maya).with(csrf()).header(HttpHeaders.IF_MATCH, "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"archived\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fields.status").exists());
        mvc.perform(post(CASE_008 + "/status").session(maya).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"planning\"}"))
                .andExpect(status().isPreconditionRequired());
        // Outside the care team: the shared 403, before any precondition or transition check.
        mvc.perform(post(CASE_021 + "/status").session(maya).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"closed\"}"))
                .andExpect(status().isForbidden());
    }
}
