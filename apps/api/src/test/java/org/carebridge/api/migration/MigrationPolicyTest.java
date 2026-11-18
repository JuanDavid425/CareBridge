package org.carebridge.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Enforces the forward-only migration policy (apps/api/README.md#migrations):
 * versioned files only, contiguous version numbers, no undo scripts, no destructive SQL.
 */
class MigrationPolicyTest {

    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__[a-z0-9_]+\\.sql");
    private static final Pattern DESTRUCTIVE =
            Pattern.compile("\\b(DROP\\s+(TABLE|SCHEMA|DATABASE)|TRUNCATE)\\b", Pattern.CASE_INSENSITIVE);

    private static List<Resource> migrations;

    @BeforeAll
    static void loadMigrations() throws IOException {
        migrations = Arrays.asList(new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/*"));
    }

    @Test
    void thereIsAtLeastOneMigration() {
        assertThat(migrations).isNotEmpty();
    }

    @Test
    void everyFileIsAVersionedMigration() {
        assertThat(migrations)
                .extracting(Resource::getFilename)
                .allMatch(name -> VERSIONED.matcher(name).matches(),
                        "named V<n>__<lower_snake_description>.sql (no U undo or R repeatable scripts)");
    }

    @Test
    void versionsStartAtOneAndHaveNoGaps() {
        List<Integer> versions = migrations.stream()
                .map(Resource::getFilename)
                .map(VERSIONED::matcher)
                .filter(Matcher::matches)
                .map(m -> Integer.parseInt(m.group(1)))
                .sorted()
                .toList();

        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)).as("migration version at position %d", i).isEqualTo(i + 1);
        }
    }

    @Test
    void migrationsContainNoDestructiveStatements() throws IOException {
        for (Resource migration : migrations) {
            String sql = migration.getContentAsString(StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
            assertThat(DESTRUCTIVE.matcher(sql).find())
                    .as("%s must not drop or truncate; write a new forward migration", migration.getFilename())
                    .isFalse();
        }
    }
}
