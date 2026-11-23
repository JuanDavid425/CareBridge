package org.carebridge.api.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * The error envelope returned by every endpoint:
 * {@code {"error": {"code": "forbidden", "message": "...", "requestId": "..."}}}.
 * Codes are stable and safe to branch on; messages are for people and never echo input.
 */
@Schema(description = "Error envelope")
public record ApiError(Body error) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Body(
            @Schema(example = "forbidden") String code,
            @Schema(example = "You do not have access to this resource.") String message,
            @Schema(example = "3f1c2a9e-5b7d-4c11-9f0e-2d8b6a1e4c77") String requestId,
            @Schema(description = "Field name to problem, for validation errors") Map<String, String> fields) {
    }

    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(new Body(code, message, requestId, Map.of()));
    }

    public static ApiError validation(Map<String, String> fields, String requestId) {
        return new ApiError(new Body("validation_failed", "Some fields are missing or invalid.", requestId, fields));
    }
}
