package fr.florianpal.fauction.configurations;

import fr.florianpal.fauction.enums.CommandKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandsConfigTest {

    private CommandsConfig config;

    private Logger logger;

    private List<LogRecord> logs;

    @BeforeEach
    void setUp() {
        config = new CommandsConfig();
        logs = new ArrayList<>();

        logger = Logger.getLogger("CommandsConfigTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @Test
    @DisplayName("A configuration without a commands section keeps the names of the plugin")
    void missingSectionKeepsTheHistoricalNames() {

        config.load(TestConfigs.of("lang: \"en\"\n"), logger);

        assertEquals(List.of("ah", "hdv"), config.names(CommandKey.ROOT));
        assertEquals("ah|hdv", config.syntax(CommandKey.ROOT));
        assertEquals("ah", config.primary(CommandKey.ROOT));

        assertEquals("list", config.syntax(CommandKey.LIST));
        assertEquals("search", config.syntax(CommandKey.SEARCH));
        assertEquals("sell", config.syntax(CommandKey.SELL));
        assertEquals("expire", config.syntax(CommandKey.EXPIRE));
        assertEquals("help", config.syntax(CommandKey.HELP));
        assertEquals("admin", config.syntax(CommandKey.ADMIN));

        assertTrue(logs.isEmpty(), "Nothing to warn about : " + messages());
    }

    @Test
    @DisplayName("The names of the file win, the first one being the main one")
    void fileNamesWin() {

        config.load(TestConfigs.of("""
                commands:
                  root: ["hdv", "ah", "marche"]
                  sell: ["vendre", "sell"]
                """), logger);

        assertEquals("hdv|ah|marche", config.syntax(CommandKey.ROOT));
        assertEquals("hdv", config.primary(CommandKey.ROOT));

        assertEquals("vendre|sell", config.syntax(CommandKey.SELL));
        assertEquals("vendre", config.primary(CommandKey.SELL));

        // A key the file does not mention keeps its own name.
        assertEquals("list", config.syntax(CommandKey.LIST));
    }

    @Test
    @DisplayName("A name is trimmed, lowercased, and never repeated")
    void namesAreNormalized() {

        config.load(TestConfigs.of("""
                commands:
                  root: ["  AH  ", "Hdv", "ah"]
                """), logger);

        assertEquals("ah|hdv", config.syntax(CommandKey.ROOT));
    }

    @Test
    @DisplayName("A name the framework could not register is ignored, the others are kept")
    void unusableNamesAreIgnored() {

        config.load(TestConfigs.of("""
                commands:
                  root: ["market", "", "two words", "a|b", "ah:market", "<market>", "mar$ket"]
                """), logger);

        assertEquals("market", config.syntax(CommandKey.ROOT));
        assertEquals(6, warnings().size(), "One warning per refused name : " + messages());
    }

    @Test
    @DisplayName("A command left without a single usable name falls back on its own")
    void anEmptyListFallsBackOnTheDefaults() {

        config.load(TestConfigs.of("""
                commands:
                  root: []
                  sell: ["  "]
                """), logger);

        assertEquals("ah|hdv", config.syntax(CommandKey.ROOT));
        assertEquals("sell", config.syntax(CommandKey.SELL));
        assertFalse(warnings().isEmpty());
    }

    @Test
    @DisplayName("A name written as a single value instead of a list falls back on the defaults")
    void aScalarIsNotAList() {

        config.load(TestConfigs.of("""
                commands:
                  root: ah
                """), logger);

        assertEquals("ah|hdv", config.syntax(CommandKey.ROOT));
        assertFalse(warnings().isEmpty());
    }

    @Test
    @DisplayName("Two subcommands sharing a name send the whole section back to the defaults")
    void aCollisionIsRefused() {

        // Falling back on the default of the offending key alone would not do : the default of
        // "sell" is exactly the name "list" took.
        config.load(TestConfigs.of("""
                commands:
                  root: ["market"]
                  list: ["sell"]
                  sell: ["sell"]
                """), logger);

        assertEquals("ah|hdv", config.syntax(CommandKey.ROOT));
        assertEquals("list", config.syntax(CommandKey.LIST));
        assertEquals("sell", config.syntax(CommandKey.SELL));

        assertTrue(logs.stream().anyMatch(record -> record.getLevel() == Level.SEVERE),
                "The owner has to be told why their whole section was dropped : " + messages());
    }

    @Test
    @DisplayName("A subcommand may be named after the root : they are not on the same level")
    void theRootDoesNotCollideWithASubCommand() {

        config.load(TestConfigs.of("""
                commands:
                  root: ["ah"]
                  list: ["ah"]
                """), logger);

        assertEquals("ah", config.syntax(CommandKey.ROOT));
        assertEquals("ah", config.syntax(CommandKey.LIST));
    }

    @Test
    @DisplayName("Two loads of the same file give the same snapshot, a changed one does not")
    void snapshotsTellTheReloadWhatChanged() {

        config.load(TestConfigs.of("commands:\n  root: [\"ah\"]\n"), logger);
        var before = config.snapshot();

        config.load(TestConfigs.of("commands:\n  root: [\"ah\"]\n"), logger);
        assertEquals(before, config.snapshot());

        config.load(TestConfigs.of("commands:\n  root: [\"market\"]\n"), logger);
        assertFalse(before.equals(config.snapshot()));
    }

    private List<LogRecord> warnings() {
        return logs.stream().filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue()).toList();
    }

    private List<String> messages() {
        return logs.stream().map(LogRecord::getMessage).toList();
    }
}
