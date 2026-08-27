package fr.florianpal.fauction.languages;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.managers.ConfigurationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
     * acf-core and acf-minecraft sections still there. The four shipped files are used as they were,
     * not a hand-made sample : the Russian and Chinese ones carry escaped multi-line strings, the
     * French one unquoted continuation lines, and all four the malformed
     * acf-minecraft.player_is_vanished_confirm key nested inside its own section.
     */
    private String legacyFile(String code) throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/lang/legacy_" + code + ".yml"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String legacyFile() throws IOException {
        return legacyFile("en");
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

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "ru", "zhcn"})
    @DisplayName("A file inherited from the ACF era is migrated with no administrator action")
    void legacyFileIsMigrated(String code) throws IOException {
        Lang lang = loadWith(code, legacyFile(code));
        String file = fileOf(code);

        assertEquals("2", versionOf(code), "the file is versioned from now on");
        assertFalse(file.contains("acf-minecraft"), "the unused section is gone");
        assertFalse(file.contains("acf-core"), "the framework section is gone");

        assertTrue(lang.raw("fauction.error.permission_denied").isPresent(), "the framework messages are there");
        assertTrue(lang.raw("fauction.help.available_commands").isPresent(), "the help texts are there");

        // Every key that is really sent, in the language of the file — the messages of the plugin
        // survive the update in the four languages, not only in English.
        for (MessageKeys key : MessageKeys.values()) {
            if (key != MessageKeys.DATABASEERROR) {
                assertTrue(lang.raw(key.getKey()).isPresent(), code + " lost " + key.getKey());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "en, There is currently no active sale.",
            "fr, Il n'y a actuellement aucune vente active.",
            "ru, В настоящее время нет активных предметов на аукционе.",
            "zhcn, 当前无物品正在出售."
    })
    @DisplayName("The texts of the file are read back in their own language and encoding")
    void migratedFileKeepsItsOwnTexts(String code, String noAuction) throws IOException {
        Lang lang = loadWith(code, legacyFile(code));

        assertEquals(noAuction, lang.raw("fauction.no_auction").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "ru", "zhcn"})
    @DisplayName("The file is copied aside before its first versioned write")
    void legacyFileIsBackedUp(String code) throws IOException {
        loadWith(code, legacyFile(code));

        File backup = dataFolder.resolve("lang_" + code + ".yml.bak").toFile();
        assertTrue(backup.exists(), "a backup is left next to the file");
        assertEquals(legacyFile(code), Files.readString(backup.toPath(), StandardCharsets.UTF_8));
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

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "ru", "zhcn"})
    @DisplayName("A file already migrated is left alone")
    void migratedFileIsIdempotent(String code) throws IOException {
        loadWith(code, legacyFile(code));
        String afterFirstLoad = fileOf(code);

        Lang lang = new Lang();
        lang.load(plugin);

        assertEquals(afterFirstLoad, fileOf(code), "a restart does not replay the relocations");
        assertTrue(lang.raw("fauction.error.permission_denied").isPresent());
    }

    @ParameterizedTest
    @CsvSource({
            "en, You opened the auction house.",
            "fr, Vous avez ouvert l'hotel des ventes.",
            "ru, Вы открыли аукцион.",
            "zhcn, 你打开了拍卖市场."
    })
    @DisplayName("A fresh installation writes the shipped file")
    void freshInstallWritesTheFile(String code, String auctionOpen) throws IOException {
        Lang lang = loadWith(code, null);

        assertEquals("2", versionOf(code));
        assertEquals(auctionOpen, lang.raw("fauction.auction_open").orElseThrow());
        assertFalse(dataFolder.resolve("lang_" + code + ".yml.bak").toFile().exists(), "nothing to back up");
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
