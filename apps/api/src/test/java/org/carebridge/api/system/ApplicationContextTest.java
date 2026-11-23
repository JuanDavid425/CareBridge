package org.carebridge.api.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void livenessProbeIsUp() {
        ResponseEntity<JsonNode> response = http.getForEntity("/actuator/health/liveness", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    }

    @Test
    void healthDoesNotExposeComponentDetails() {
        ResponseEntity<JsonNode> response = http.getForEntity("/actuator/health", JsonNode.class);

        assertThat(response.getBody().has("components")).isFalse();
        assertThat(response.getBody().has("details")).isFalse();
    }

    @Test
    void systemInfoReportsSyntheticOnlyMode() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/v1/system/info", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.path("dataMode").asText()).isEqualTo("SYNTHETIC_ONLY");
        assertThat(body.path("environment").asText()).isEqualTo("test");
        assertThat(body.path("aiProvider").asText()).isEqualTo("mock");
    }

    @Test
    void openApiDocumentDescribesTheApi() {
        ResponseEntity<JsonNode> response = http.getForEntity("/v3/api-docs", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.path("info").path("title").asText()).isEqualTo("CareBridge API");
        assertThat(body.path("paths").has("/api/v1/system/info")).isTrue();
    }

    @Test
    void otherActuatorEndpointsAreNotReadable() {
        // Not exposed over HTTP, and denied by Spring Security before routing.
        for (String path : new String[] {"/actuator/env", "/actuator/beans"}) {
            ResponseEntity<String> response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).as(path).doesNotContain("propertySources", "beans\":");
        }
    }

    @Test
    void securityHeadersAreSet() {
        ResponseEntity<String> response = http.getForEntity("/api/v1/system/info", String.class);

        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
