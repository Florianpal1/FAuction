package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import fr.florianpal.fauction.enums.CommandKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The config.yml of a server that is already running, updated against the one the plugin ships.
 * A new section has to arrive by itself, without touching a single value the administrator wrote.
 */
class ConfigUpdateTest {

    /**
     * A config.yml of version 11 : the last one before the commands became configurable, with
     * values the administrator changed.
     */
    private static final String VERSION_11 = """
            version: 11
            lang: "fr"
            defaultGui: EXPIRE
            decimalFormat: "0.###"
            limitations:
              default: 12
            """;

    @Test
    @DisplayName("An older config.yml gains the commands section and keeps its own values")
    void theCommandsSectionArrivesOnUpdate() throws IOException {

        YamlDocument updated = update(VERSION_11);

        assertEquals(List.of("ah", "hdv"), updated.getStringList("commands.root"));
        assertEquals(List.of("sell"), updated.getStringList("commands.sell"));
        assertEquals("12", String.valueOf(updated.get("version")));

        // Nothing the administrator wrote moved.
        assertEquals("fr", updated.getString("lang"));
        assertEquals("EXPIRE", updated.getString("defaultGui"));
        assertEquals("0.###", updated.getString("decimalFormat"));
        assertEquals(12, updated.getInt("limitations.default"));
    }

    @Test
    @DisplayName("Names already customised survive the update")
    void customisedNamesAreKept() throws IOException {

        YamlDocument updated = update("""
                version: 12
                lang: "en"
                commands:
                  root: ["market"]
                  sell: ["vendre", "sell"]
                """);

        assertEquals(List.of("market"), updated.getStringList("commands.root"));
        assertEquals(List.of("vendre", "sell"), updated.getStringList("commands.sell"));

        CommandsConfig commandsConfig = new CommandsConfig();
        commandsConfig.load(updated, Logger.getLogger("ConfigUpdateTest"));

        assertEquals("market", commandsConfig.syntax(CommandKey.ROOT));
        assertEquals("vendre|sell", commandsConfig.syntax(CommandKey.SELL));
        assertEquals("list", commandsConfig.syntax(CommandKey.LIST));
    }

    /**
     * The very settings {@code ConfigurationManager} uses for config.yml, against the resource the
     * jar ships.
     */
    private static YamlDocument update(String existingFile) throws IOException {
        try (InputStream shipped = Objects.requireNonNull(ConfigUpdateTest.class.getResourceAsStream("/config.yml"))) {
            return YamlDocument.create(
                    new ByteArrayInputStream(existingFile.getBytes(StandardCharsets.UTF_8)),
                    shipped,
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder()
                            .setVersioning(new BasicVersioning("version"))
                            .setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING)
                            .build()
            );
        }
    }
}
