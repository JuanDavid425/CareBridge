package org.carebridge.api.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import org.carebridge.api.config.CareBridgeProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Service information")
public class SystemInfoController {

    private final CareBridgeProperties properties;
    private final Optional<BuildProperties> build;

    public SystemInfoController(CareBridgeProperties properties, ObjectProvider<BuildProperties> build) {
        this.properties = properties;
        this.build = Optional.ofNullable(build.getIfAvailable());
    }

    @GetMapping("/info")
    @Operation(summary = "Service name, version and data mode")
    public SystemInfo info() {
        return new SystemInfo(
                build.map(BuildProperties::getArtifact).orElse("carebridge-api"),
                build.map(BuildProperties::getVersion).orElse("dev"),
                properties.environment(),
                SystemInfo.DataMode.SYNTHETIC_ONLY,
                properties.aiProvider());
    }
}
