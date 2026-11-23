package org.carebridge.api.security;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.carebridge.api.testsupport.InMemoryIdentityStore;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InMemoryIdentityStore identities;

    private static String credentials(String handle, String password) {
        return """
                {"handle": "%s", "password": "%s"}""".formatted(handle, password);
    }

    private MockHttpSession signIn(String handle) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(handle, InMemoryIdentityStore.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    void signInReturnsTheAccountWithUnitsAndPermissions() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("maya.coordinator", InMemoryIdentityStore.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("maya.coordinator"))
                .andExpect(jsonPath("$.role").value("coordinator"))
                .andExpect(jsonPath("$.units", hasSize(1)))
                .andExpect(jsonPath("$.units[0].code").value("DEMO-UNIT-01"))
                .andExpect(jsonPath("$.permissions", hasItem("case.create")))
                .andExpect(jsonPath("$.permissions", not(hasItem("proposal.review"))))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void handlesAreCaseInsensitive() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("  Maya.Coordinator ", InMemoryIdentityStore.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("maya.coordinator"));
    }

    @Test
    void theSessionAuthenticatesLaterRequests() throws Exception {
        MockHttpSession session = signIn("okafor.reviewer");

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("reviewer"))
                .andExpect(jsonPath("$.permissions", hasItem("proposal.review")));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "wrong password,      maya.coordinator,    not-the-password",
            "unknown handle,      nobody.here,         test-password-123",
            "inactive account,    former.coordinator,  test-password-123",
            "no password set,     nopassword.reviewer, test-password-123"
    })
    void failedSignInsAreIndistinguishable(String reason, String handle, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(handle, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_credentials"))
                .andExpect(jsonPath("$.error.message").value("Handle or password is incorrect."))
                .andExpect(jsonPath("$.error.requestId", notNullValue()));
    }

    @Test
    void signInWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("maya.coordinator", InMemoryIdentityStore.PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("csrf_token_invalid"));
    }

    @Test
    void blankFieldsAreAValidationError() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("", "")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("validation_failed"))
                .andExpect(jsonPath("$.error.fields.handle", notNullValue()))
                .andExpect(jsonPath("$.error.fields.password", notNullValue()));
    }

    @Test
    void csrfEndpointIssuesTheCookie() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void anonymousRequestsGetA401Envelope() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"))
                .andExpect(jsonPath("$.error.requestId", notNullValue()));
    }

    @Test
    void careTeamMembersListTheirUnits() throws Exception {
        mvc.perform(get("/api/v1/me/units").session(signIn("maya.coordinator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("DEMO-UNIT-01")));
    }

    @ParameterizedTest
    @CsvSource({"alvarez.patient", "lee.admin"})
    void accountsOutsideTheCareTeamCannotListUnits(String handle) throws Exception {
        mvc.perform(get("/api/v1/me/units").session(signIn(handle)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void adminReadsTheRoleMatrix() throws Exception {
        mvc.perform(get("/api/v1/admin/role-matrix").session(signIn("lee.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(15)))
                .andExpect(jsonPath("$[?(@.role == 'patient')].scope", containsInAnyOrder("own_case")));
    }

    @ParameterizedTest
    @CsvSource({"maya.coordinator", "okafor.reviewer", "alvarez.patient"})
    void onlyAdminsReadTheRoleMatrix(String handle) throws Exception {
        mvc.perform(get("/api/v1/admin/role-matrix").session(signIn(handle)))
                .andExpect(status().isForbidden());
    }

    @Test
    void signOutEndsTheSession() throws Exception {
        MockHttpSession session = signIn("maya.coordinator");

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatingAnAccountEndsItsSession() throws Exception {
        MockHttpSession session = signIn("leaving.coordinator");

        identities.deactivate("leaving.coordinator");

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
        assertThat(session.isInvalid()).isTrue();
    }
}
