package fr.florianpal.fauction.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {"V_2.2.2", "v2.2.2", "2.2.2", "V2.2.2"})
    @DisplayName("A release version is read with or without its prefix")
    void releaseIsParsed(String raw) {
        assertEquals(new PluginVersion(2, 2, 2), PluginVersion.parseRelease(raw).orElseThrow());
    }

    @Test
    @DisplayName("A missing component counts as 0")
    void missingComponentIsZero() {
        assertEquals(new PluginVersion(2, 2, 0), PluginVersion.parseRelease("2.2").orElseThrow());
        assertEquals(new PluginVersion(2, 2, 0), PluginVersion.parseRelease("V2.2").orElseThrow());
        assertEquals(new PluginVersion(3, 0, 0), PluginVersion.parseRelease("V_3").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.2.2-SNAPSHOT", "V_2.3.0-beta", "2.3.0-rc1", "2.3.0.1"})
    @DisplayName("A version with a suffix is not a release")
    void suffixIsRefused(String raw) {
        assertTrue(PluginVersion.parseRelease(raw).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "V_", "v", ".2.2", "2..2", "99999999999.0.0"})
    @DisplayName("An unreadable version gives an empty result instead of throwing")
    void unreadableIsEmpty(String raw) {
        assertTrue(PluginVersion.parseRelease(raw).isEmpty());
    }

    @Test
    @DisplayName("Versions are compared numerically, not as strings")
    void numericComparison() {
        // V_1.9.10 is a real tag of the repository.
        assertGreater("1.9.10", "1.9.9");
        assertGreater("2.2.2", "2.2.1");
        assertGreater("2.10.0", "2.9.9");
        assertGreater("3.0.0", "2.99.99");
        assertEquals(0, version("V_2.2.2").compareTo(version("2.2.2")));
    }

    @Test
    @DisplayName("The version is printed without prefix")
    void toStringHasNoPrefix() {
        assertEquals("2.2.2", version("V_2.2.2").toString());
        assertEquals("2.2.0", version("2.2").toString());
    }

    private void assertGreater(String greater, String lower) {
        assertTrue(version(greater).compareTo(version(lower)) > 0, greater + " > " + lower);
        assertTrue(version(lower).compareTo(version(greater)) < 0, lower + " < " + greater);
    }

    private PluginVersion version(String raw) {
        return PluginVersion.parseRelease(raw).orElseThrow();
    }
}
