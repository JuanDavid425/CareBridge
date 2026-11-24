package org.carebridge.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.carebridge.api.testsupport.InMemoryIdentityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The browser's CSRF round trip without the {@code csrf()} test post-processor: read the
 * {@code XSRF-TOKEN} cookie, echo it in {@code X-XSRF-TOKEN}.
 *
 * <p>Runs in its own application context. {@code csrf()} swaps the token repository of the
 * shared context's CSRF filter, so after other test classes have used it the real cookie is no
 * longer written and this test would pass or fail depending on class order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieFlowTest {

    private static final String CREDENTIALS = """
            {"handle": "maya.coordinator", "password": "%s"}""".formatted(InMemoryIdentityStore.PASSWORD);

    @Autowired
    private MockMvc mvc;

    private Cookie fetchToken() throws Exception {
        Cookie token = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        assertThat(token.getValue()).isNotBlank();
        assertThat(token.isHttpOnly()).as("the SPA must be able to read the token").isFalse();
        return token;
    }

    @Test
    void echoingTheCookieInTheHeaderAllowsSignIn() throws Exception {
        Cookie token = fetchToken();
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("maya.coordinator"));
    }

    @Test
    void aHeaderThatDoesNotMatchTheCookieIsRejected() throws Exception {
        Cookie token = fetchToken();
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue() + "x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("csrf_token_invalid"));
    }

    @Test
    void theCookieAloneIsNotEnough() throws Exception {
        Cookie token = fetchToken();
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("csrf_token_invalid"));
    }
}
