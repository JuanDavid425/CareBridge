package org.carebridge.api.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo account seeding ({@code carebridge.demo.*}). Off by default.
 *
 * @param enabled  load the synthetic seed's care units, accounts and memberships on startup
 * @param seedFile path to {@code packages/synthetic-data/seed/demo-seed.json}
 * @param password shared sign-in password for every demo account, from the environment only;
 *                 the seed file itself never contains credentials
 */
@ConfigurationProperties(prefix = "carebridge.demo")
public record DemoProperties(boolean enabled, String seedFile, String password) {
}
