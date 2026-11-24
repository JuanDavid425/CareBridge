package org.carebridge.api.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdFilterTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mvc;

    @Test
    void generatesAnIdWhenNoneIsSent() throws Exception {
        mvc.perform(get("/api/v1/system/info"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void keepsAWellFormedIncomingId() throws Exception {
        mvc.perform(get("/api/v1/system/info").header(RequestIdFilter.HEADER, "web-7f3a9c21"))
                .andExpect(header().string(RequestIdFilter.HEADER, "web-7f3a9c21"));
    }

    @Test
    void replacesMalformedIncomingIds() throws Exception {
        mvc.perform(get("/api/v1/system/info").header(RequestIdFilter.HEADER, "bad id\r\nX-Injected: 1"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void errorEnvelopesCarryTheSameIdAsTheHeader() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header(RequestIdFilter.HEADER, "trace-0001-abcd"))
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-0001-abcd"))
                .andExpect(jsonPath("$.error.requestId").value("trace-0001-abcd"));
    }
}
