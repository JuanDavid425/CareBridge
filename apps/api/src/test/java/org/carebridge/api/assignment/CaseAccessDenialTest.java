package org.carebridge.api.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.carebridge.api.testsupport.InMemoryIdentityStore;
import org.carebridge.api.testsupport.TestCases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Case-assignment denials through the real filter chain: cross-unit and missing-assignment access
 * must end in the same 403 envelope as a role check, with no hint of the reason.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseAccessDenialTest {

    private static final String FORBIDDEN_MESSAGE = "You do not have access to this resource.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private MockHttpSession signIn(String handle) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"handle": "%s", "password": "%s"}""".formatted(handle, InMemoryIdentityStore.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String caseUrl(String caseRef) {
        return "/api/v1/test-only/cases/" + TestCases.caseId(caseRef);
    }

    @Test
    void assignedCoordinatorOpensTheCase() throws Exception {
        mvc.perform(get(caseUrl("DEMO-CASE-008")).param("permission", "case.read")
                        .session(signIn("maya.coordinator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(TestCases.CASE_008.caseId().toString()));
    }

    @Test
    void linkedPatientReadsThePublishedPlan() throws Exception {
        mvc.perform(get(caseUrl("DEMO-CASE-008")).param("permission", "plan.read_published")
                        .session(signIn("alvarez.patient")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}: {1} -> {2} with {3}")
    @CsvSource({
            "cross-unit coordinator,     chen.coordinator, DEMO-CASE-008, case.read",
            "cross-unit reviewer,        okafor.reviewer,  DEMO-CASE-021, proposal.review",
            "unit member not assigned,   maya.coordinator, DEMO-CASE-011, case.read",
            "reviewer not assigned,      okafor.reviewer,  DEMO-CASE-011, proposal.review",
            "assigned without the right, okafor.reviewer,  DEMO-CASE-008, task.assign",
            "administrator,              lee.admin,        DEMO-CASE-008, case.read",
            "patient not linked,         alvarez.patient,  DEMO-CASE-021, plan.read_published",
            "patient reading staff view, alvarez.patient,  DEMO-CASE-008, case.read"
    })
    void deniedAccessGetsTheForbiddenEnvelope(String reason, String handle, String caseRef, String permission)
            throws Exception {
        mvc.perform(get(caseUrl(caseRef)).param("permission", permission).session(signIn(handle)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"))
                .andExpect(jsonPath("$.error.message").value(FORBIDDEN_MESSAGE))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty())
                .andExpect(jsonPath("$.error.fields").doesNotExist())
                .andExpect(jsonPath("$.error.reason").doesNotExist())
                .andExpect(jsonPath("$.caseId").doesNotExist());
    }

    @Test
    void creatingInAnotherUnitIsDenied() throws Exception {
        MockHttpSession maya = signIn("maya.coordinator");

        mvc.perform(get("/api/v1/test-only/units/" + TestCases.UNIT_01).param("permission", "case.create")
                        .session(maya))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/test-only/units/" + TestCases.UNIT_02).param("permission", "case.create")
                        .session(maya))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void caseDenialMatchesTheRoleCheckEnvelope() throws Exception {
        MockHttpSession maya = signIn("maya.coordinator");

        JsonNode roleCheck = body(mvc.perform(get("/api/v1/admin/role-matrix").session(maya))
                .andExpect(status().isForbidden())
                .andReturn());
        JsonNode caseCheck = body(mvc.perform(get(caseUrl("DEMO-CASE-011")).param("permission", "case.read")
                        .session(maya))
                .andExpect(status().isForbidden())
                .andReturn());

        assertThat(withoutRequestId(caseCheck)).isEqualTo(withoutRequestId(roleCheck));
    }

    @Test
    void theDenialCarriesTheCallersRequestId() throws Exception {
        mvc.perform(get(caseUrl("DEMO-CASE-021")).param("permission", "case.read")
                        .header("X-Request-Id", "denial-check-0001")
                        .session(signIn("maya.coordinator")))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Request-Id", "denial-check-0001"))
                .andExpect(jsonPath("$.error.requestId").value("denial-check-0001"));
    }

    @Test
    void anonymousCallersAreAskedToSignIn() throws Exception {
        mvc.perform(get(caseUrl("DEMO-CASE-008")).param("permission", "case.read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode withoutRequestId(JsonNode body) {
        JsonNode copy = body.deepCopy();
        ((ObjectNode) copy.get("error")).remove("requestId");
        return copy;
    }
}
