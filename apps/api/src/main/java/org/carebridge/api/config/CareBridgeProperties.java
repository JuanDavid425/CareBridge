package org.carebridge.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings under {@code carebridge.*}.
 *
 * @param environment name shown in /api/v1/system/info (local, ci, staging, demo)
 * @param aiProvider  model provider switch; {@code mock} needs no credentials and is the default
 */
@Validated
@ConfigurationProperties(prefix = "carebridge")
public record CareBridgeProperties(@NotBlank String environment, @NotBlank String aiProvider) {
}
