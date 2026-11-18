package org.carebridge.api.system;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Build and runtime information that is safe to expose publicly")
public record SystemInfo(
        @Schema(example = "carebridge-api") String name,
        @Schema(example = "0.1.0-SNAPSHOT") String version,
        @Schema(example = "local") String environment,
        @Schema(description = "Always SYNTHETIC_ONLY: real patient data is never accepted")
        DataMode dataMode,
        @Schema(example = "mock") String aiProvider) {

    public enum DataMode {
        SYNTHETIC_ONLY
    }
}
