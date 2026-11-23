package org.carebridge.api.testsupport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Picked up by component scanning in tests; the "test" profile sets carebridge.identity.store=memory. */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class TestIdentityConfig {

    @Bean
    InMemoryIdentityStore identityStore(PasswordEncoder passwordEncoder) {
        return new InMemoryIdentityStore(passwordEncoder);
    }
}
