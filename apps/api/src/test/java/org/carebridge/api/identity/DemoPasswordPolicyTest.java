package org.carebridge.api.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DemoPasswordPolicyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"            ", "short-pass1"})
    void missingOrShortPasswordsAreRefused(String password) {
        assertThatThrownBy(() -> DemoSeedImporter.checkDemoPassword(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 12 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"change-me-demo-only", "change-me-local-only", " Change-Me-please-123"})
    void examplePlaceholdersAreRefused(String password) {
        assertThatThrownBy(() -> DemoSeedImporter.checkDemoPassword(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local-demo-password", "a-long-local-value"})
    void ownValuesAreAccepted(String password) {
        assertThatCode(() -> DemoSeedImporter.checkDemoPassword(password)).doesNotThrowAnyException();
    }
}
