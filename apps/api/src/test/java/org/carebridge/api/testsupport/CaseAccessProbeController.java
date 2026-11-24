package org.carebridge.api.testsupport;

import java.util.Map;
import java.util.UUID;
import org.carebridge.api.assignment.CaseAuthorizationService;
import org.carebridge.api.assignment.CaseRef;
import org.carebridge.api.security.CareBridgeUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test-only endpoint that runs the case-assignment policy through the real filter chain and
 * exception handling, until the case endpoints exist.
 */
@RestController
@Profile("test")
class CaseAccessProbeController {

    private final CaseAuthorizationService authorization;

    CaseAccessProbeController(CaseAuthorizationService authorization) {
        this.authorization = authorization;
    }

    @GetMapping("/api/v1/test-only/cases/{caseId}")
    Map<String, Object> probe(@PathVariable UUID caseId, @RequestParam String permission,
            @AuthenticationPrincipal CareBridgeUser user) {
        CaseRef target = TestCases.find(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        authorization.require(user, target, permission);
        return Map.of("caseId", caseId, "permission", permission);
    }

    @GetMapping("/api/v1/test-only/units/{unitId}")
    Map<String, Object> probeUnit(@PathVariable UUID unitId, @RequestParam String permission,
            @AuthenticationPrincipal CareBridgeUser user) {
        authorization.requireInUnit(user, unitId, permission);
        return Map.of("unitId", unitId, "permission", permission);
    }
}
