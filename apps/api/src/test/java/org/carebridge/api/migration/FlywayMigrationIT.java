package org.carebridge.api.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Applies every migration to a throwaway PostgreSQL with pgvector (same image as compose.yaml). */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.0-pg17").asCompatibleSubstituteFor("postgres"));

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
    }

    @Test
    void migratesAnEmptyDatabaseAndIsIdempotent() {
        MigrateResult first = flyway().migrate();
        assertThat(first.success).isTrue();

        MigrateResult second = flyway().migrate();
        assertThat(second.migrationsExecuted).isZero();
        flyway().validate();
    }

    @Test
    void requiredExtensionsAreInstalled() throws SQLException {
        flyway().migrate();

        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM pg_extension WHERE extname IN ('pgcrypto', 'vector')")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void cleanIsDisabled() {
        flyway().migrate();

        assertThatThrownBy(() -> flyway().clean())
                .hasMessageContaining("clean");
    }
}
