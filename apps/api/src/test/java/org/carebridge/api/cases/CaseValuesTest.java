package org.carebridge.api.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** ETags, due filters, query limits and patient-name normalization. */
class CaseValuesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 2);

    @Test
    void eTagsRoundTrip() {
        assertThat(CaseETags.of(3)).isEqualTo("\"v3\"");
        assertThat(CaseETags.parse("\"v3\"")).hasValue(3);
        assertThat(CaseETags.parse(" W/\"v12\" ")).hasValue(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "v3", "\"3\"", "\"v0\"", "\"v-1\"", "\"v3\", \"v4\"", "\"v9999999999\""})
    void unusableIfMatchValuesNeedAPrecondition(String header) {
        assertThat(CaseETags.parse(header)).isEmpty();
        assertThatThrownBy(() -> CaseETags.require(header)).isInstanceOf(PreconditionRequiredException.class);
    }

    @Test
    void missingIfMatchNeedsAPrecondition() {
        assertThatThrownBy(() -> CaseETags.require(null)).isInstanceOf(PreconditionRequiredException.class);
    }

    @ParameterizedTest(name = "{0} on {1}: {2}")
    @CsvSource({
        "overdue, 2026-03-01, true",
        "overdue, 2026-03-02, false",
        "today, 2026-03-02, true",
        "today, 2026-03-03, false",
        "week, 2026-03-02, true",
        "week, 2026-03-08, true",
        "week, 2026-03-09, false",
        "week, 2026-03-01, false",
    })
    void dueFiltersAreInclusiveDayRanges(String filter, LocalDate due, boolean matches) {
        assertThat(DueFilter.parse(filter).orElseThrow().matches(due, TODAY)).isEqualTo(matches);
    }

    @Test
    void queryLimitsAreEnforced() {
        assertThatThrownBy(() -> new CaseQuery(null, Set.of(), null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseQuery(null, Set.of(), null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CaseQuery("  Alvarez ", Set.of(), null, 2, 10).searchKey()).isEqualTo("alvarez");
        assertThat(new CaseQuery("   ", Set.of(), null, 2, 10).offset()).isEqualTo(20);
        assertThat(new CaseQuery("   ", Set.of(), null, 0, 10).search()).isNull();
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "Ana Ruiz|Ana Ruiz (DEMO)",
        "  Ana   Ruiz  |Ana Ruiz (DEMO)",
        "Ana Ruiz (demo)|Ana Ruiz (DEMO)",
        "Ana Ruiz(DEMO)|Ana Ruiz (DEMO)",
    })
    void patientNamesAlwaysCarryTheDemoMarker(String raw, String expected) {
        assertThat(CaseService.normalizePatientName(raw)).isEqualTo(expected);
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(JdbcCaseStore.escapeLike("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }
}
