package org.carebridge.api.identity;

import java.nio.file.Path;
import org.carebridge.api.config.CareBridgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Seeds demo accounts on startup when {@code carebridge.demo.enabled=true} (local and demo only). */
@Component
@ConditionalOnProperty(name = "carebridge.demo.enabled", havingValue = "true")
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final DemoProperties demo;
    private final CareBridgeProperties properties;
    private final DemoSeedImporter importer;

    public DemoAccountSeeder(DemoProperties demo, CareBridgeProperties properties, JdbcClient jdbc,
            PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager) {
        this.demo = demo;
        this.properties = properties;
        this.importer = new DemoSeedImporter(jdbc, passwordEncoder, new TransactionTemplate(transactionManager));
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if ("production".equalsIgnoreCase(properties.environment())) {
            throw new IllegalStateException("demo seeding is not allowed when CAREBRIDGE_ENV=production");
        }
        Path seedFile = Path.of(demo.seedFile());
        DemoSeedImporter.Result result = importer.importFile(seedFile, demo.password());
        // Counts only: account handles and emails are not logged.
        log.info("Seeded demo identity data: {} care units, {} accounts, {} memberships",
                result.careUnits(), result.accounts(), result.memberships());
    }
}
