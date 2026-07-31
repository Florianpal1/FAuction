package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds a configuration from a yaml written inline, with the same settings as the plugin.
 */
final class TestConfigs {

    private TestConfigs() {
    }

    static YamlDocument of(String yaml) {
        try {
            return YamlDocument.create(
                    new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                    GeneralSettings.builder().setUseDefaults(false).build()
            );
        } catch (IOException e) {
            throw new IllegalStateException("Invalid test configuration", e);
        }
    }
}