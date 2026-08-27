package fr.florianpal.fauction.languages;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.managers.ConfigurationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The update of the language file of a server that already exists. This is the only place where the
 * plugin rewrites a file the administrator may have edited by hand, so it gets its own battery.
 */
class LangMigrationTest {

    @TempDir
    Path dataFolder;

    private FAuction plugin;

    private GlobalConfig globalConfig;

    @BeforeEach
    void setUp() {
        plugin = mock(FAuction.class);
        globalConfig = mock(GlobalConfig.class);
        ConfigurationManager configurationManager = mock(ConfigurationManager.class);

        when(configurationManager.getGlobalConfig()).thenReturn(globalConfig);
        when(plugin.getConfigurationManager()).thenReturn(configurationManager);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LangMigrationTest"));
    }

    /**
     * A language file as it is found on a server installed in the ACF era : no version key, the
     * acf-core and acf-minecraft sections still there.
     */
    private String legacyFile() throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/lang/legacy_en.yml"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Lang loadWith(String code, String content) throws IOException {
        when(globalConfig.getLang()).thenReturn(code);
        if (content != null) {
            Files.writeString(dataFolder.resolve("lang_" + code + ".yml"), content, StandardCharsets.UTF_8);
        }

        Lang lang = new Lang();
        lang.load(plugin);
        return lang;
    }

    private String fileOf(String code) throws IOException {
        return Files.readString(dataFolder.resolve("lang_" + code + ".yml"), StandardCharsets.UTF_8);
    }

    /**
     * The version as the updater reads it back. Read through the document rather than from the text :
     * an updated file gets it quoted, a freshly written one does not, and only the value matters.
     */
    private String versionOf(String code) throws IOException {
        return YamlDocument.create(dataFolder.resolve("lang_" + code + ".yml").toFile()).getString("version");
    }

    @Test
    @DisplayName("A file inherited from the ACF era is migrated with no administrator action")
    void legacyFileIsMigrated() throws IOException {
        Lang lang = loadWith("en", legacyFile());
        String file = fileOf("en");

        assertEquals("2", versionOf("en"), "the file is versioned from now on");
        assertFalse(file.contains("acf-minecraft"), "the unused section is gone");
        assertFalse(file.contains("acf-core"), "the framework section is gone");

        assertTrue(lang.raw("fauction.error.permission_denied").isPresent(), "the framework messages are there");
        assertTrue(lang.raw("fauction.help.available_commands").isPresent(), "the help texts are there");
        assertEquals("There is currently no active sale.", lang.raw("fauction.no_auction").orElseThrow());
    }

    @Test
    @DisplayName("The file is copied aside before its first versioned write")
    void legacyFileIsBackedUp() throws IOException {
        loadWith("en", legacyFile());

        File backup = dataFolder.resolve("lang_en.yml.bak").toFile();
        assertTrue(backup.exists(), "a backup is left next to the file");
        assertEquals(legacyFile(), Files.readString(backup.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("A message customised by the administrator survives the update")
    void customisedMessageIsPreserved() throws IOException {
        String customised = legacyFile().replace(
                "spam: \"&cStop! You are trying to perform too many actions in a very short time! Please wait a few seconds...\"",
                "spam: \"&4Slow down.\"");

        Lang lang = loadWith("en", customised);

        assertEquals("§4Slow down.", lang.message(MessageKeys.SPAM).orElseThrow());
    }

    @Test
    @DisplayName("A customised acf-core message is found again under fauction.error")
    void customisedFrameworkMessageIsRelocated() throws IOException {
        String customised = legacyFile().replace(
                "permission_denied: \"I'm sorry, but you do not have permission to perform this command.\"",
                "permission_denied: \"Nope.\"");

        Lang lang = loadWith("en", customised);

        assertEquals("Nope.", lang.raw("fauction.error.permission_denied").orElseThrow());
    }

    @Test
    @DisplayName("A file already migrated is left alone")
    void migratedFileIsIdempotent() throws IOException {
        loadWith("en", legacyFile());
        String afterFirstLoad = fileOf("en");

        Lang lang = new Lang();
        lang.load(plugin);

        assertEquals(afterFirstLoad, fileOf("en"), "a restart does not replay the relocations");
        assertTrue(lang.raw("fauction.error.permission_denied").isPresent());
    }

    @Test
    @DisplayName("A fresh installation writes the shipped file")
    void freshInstallWritesTheFile() throws IOException {
        Lang lang = loadWith("fr", null);

        assertEquals("2", versionOf("fr"));
        assertEquals("Vous avez ouvert l'hotel des ventes.", lang.raw("fauction.auction_open").orElseThrow());
        assertFalse(dataFolder.resolve("lang_fr.yml.bak").toFile().exists(), "nothing to back up");
    }

    @Test
    @DisplayName("A language the plugin does not ship keeps its own keys and is completed in English")
    void customLanguageIsPreserved() throws IOException {
        String german = """
                fauction:
                  auction_open: "Du hast das Auktionshaus geöffnet."
                  no_auction: "Derzeit gibt es keine Verkäufe."
                """;

        Lang lang = loadWith("de", german);

        assertEquals("Du hast das Auktionshaus geöffnet.", lang.raw("fauction.auction_open").orElseThrow());
        assertEquals("Derzeit gibt es keine Verkäufe.", lang.raw("fauction.no_auction").orElseThrow());
        // The holes are filled with the English texts : a message in English beats a message that is
        // never sent, given that an absent key sends nothing.
        assertEquals("You cannot buy your own item.", lang.raw("fauction.buy_your_item").orElseThrow());
    }
}
