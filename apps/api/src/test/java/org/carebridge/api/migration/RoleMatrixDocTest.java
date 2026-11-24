package org.carebridge.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** docs/security/role-matrix.md must list exactly the rows that V3 inserts into role_permissions. */
class RoleMatrixDocTest {

    private static final Path DOC = Path.of("..", "..", "docs", "security", "role-matrix.md");
    private static final Pattern SQL_ROW =
            Pattern.compile("\\(\\s*'([a-z]+)',\\s*'([a-z._]+)',\\s*'([a-z_]+)'\\s*\\)");
    private static final Pattern DOC_ROW =
            Pattern.compile("^\\|\\s*([a-z]+)\\s*\\|\\s*([a-z._]+)\\s*\\|\\s*([a-z_]+)\\s*\\|$");

    private static Set<String> migrationRows() throws IOException {
        String sql = new ClassPathResource("db/migration/V3__role_permissions.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        // Only the VALUES list: the CHECK constraints above it also contain quoted triples.
        int insert = sql.indexOf("INSERT INTO role_permissions");
        assertThat(insert).isNotNegative();
        Set<String> rows = new TreeSet<>();
        Matcher m = SQL_ROW.matcher(sql.substring(insert));
        while (m.find()) {
            rows.add(m.group(1) + " " + m.group(2) + " " + m.group(3));
        }
        return rows;
    }

    private static Set<String> documentedRows() throws IOException {
        String doc = Files.readString(DOC, StandardCharsets.UTF_8);
        int start = doc.indexOf("<!-- role-matrix:start -->");
        int end = doc.indexOf("<!-- role-matrix:end -->");
        assertThat(start).as("role-matrix markers in %s", DOC).isNotNegative();
        assertThat(end).isGreaterThan(start);
        Set<String> rows = new TreeSet<>();
        for (String line : doc.substring(start, end).split("\n")) {
            Matcher m = DOC_ROW.matcher(line.strip());
            if (m.matches()) {
                rows.add(m.group(1) + " " + m.group(2) + " " + m.group(3));
            }
        }
        return rows;
    }

    @Test
    void documentedMatrixMatchesTheMigration() throws IOException {
        Set<String> migration = migrationRows();
        assertThat(migration).hasSize(15);
        assertThat(documentedRows()).isEqualTo(migration);
    }
}
