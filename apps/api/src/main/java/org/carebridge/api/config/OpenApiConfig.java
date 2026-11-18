package org.carebridge.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI careBridgeOpenApi(@Value("${carebridge.api-version:0.1.0}") String version) {
        return new OpenAPI().info(new Info()
                .title("CareBridge API")
                .version(version)
                .description("Post-discharge care coordination. Synthetic data only: not for real "
                        + "patient information, not clinical decision support.")
                .license(new License().name("Educational demo")));
    }
}
