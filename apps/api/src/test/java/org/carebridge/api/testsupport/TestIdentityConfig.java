package org.carebridge.api.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Picked up by component scanning in tests; the "test" profile sets carebridge.identity.store and
 * carebridge.cases.store to memory. "Today" is fixed so due filters and overdue flags are stable.
 */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class TestIdentityConfig {

    @Bean
    InMemoryIdentityStore identityStore(PasswordEncoder passwordEncoder) {
        return new InMemoryIdentityStore(passwordEncoder);
    }

    /** 2026-03-02, a Monday. */
    public static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");

    @Bean
    @Primary
    Clock testClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    InMemoryCaseStore caseStore(InMemoryIdentityStore identities, Clock clock) {
        return new InMemoryCaseStore(identities, clock);
    }
}
