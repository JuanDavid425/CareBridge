package org.carebridge.api.testsupport;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A migrated PostgreSQL database for *IT tests: the pgvector image through Testcontainers, or,
 * when {@code CAREBRIDGE_IT_DB_URL} is set, an existing empty database (machines without Docker).
 * {@code CAREBRIDGE_IT_DB_USER}, {@code CAREBRIDGE_IT_DB_PASSWORD} and
 * {@code CAREBRIDGE_IT_FLYWAY_LOCATIONS} go with it. Without either, the test class is skipped.
 */
public final class PostgresForTests implements AutoCloseable {

    private final PostgreSQLContainer<?> container;
    private final DataSource dataSource;

    private PostgresForTests(PostgreSQLContainer<?> container, DataSource dataSource) {
        this.container = container;
        this.dataSource = dataSource;
    }

    public static PostgresForTests startMigrated() {
        String url = System.getenv("CAREBRIDGE_IT_DB_URL");
        String user;
        String password;
        PostgreSQLContainer<?> container = null;
        if (url != null && !url.isBlank()) {
            user = System.getenv().getOrDefault("CAREBRIDGE_IT_DB_USER", "carebridge");
            password = System.getenv().getOrDefault("CAREBRIDGE_IT_DB_PASSWORD", "");
        } else {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "Docker or CAREBRIDGE_IT_DB_URL is needed for database tests");
            container = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:0.8.0-pg17").asCompatibleSubstituteFor("postgres"));
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        Flyway.configure()
                .dataSource(url, user, password)
                .locations(System.getenv().getOrDefault("CAREBRIDGE_IT_FLYWAY_LOCATIONS", "classpath:db/migration"))
                .load()
                .migrate();
        return new PostgresForTests(container, new DriverManagerDataSource(url, user, password));
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        if (container != null) {
            container.stop();
        }
    }
}
