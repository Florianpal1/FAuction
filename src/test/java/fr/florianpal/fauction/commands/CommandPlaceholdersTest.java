package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.configurations.CommandsConfig;
import fr.florianpal.fauction.configurations.TestConfigs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandPlaceholdersTest {

    private static final Logger LOGGER = Logger.getLogger("CommandPlaceholdersTest");

    @Test
    @DisplayName("A placeholder becomes the names configured for that command")
    void placeholdersBecomeTheConfiguredNames() {

        CommandPlaceholders placeholders = placeholders("""
                commands:
                  root: ["market", "ah"]
                  sell: ["vendre", "sell"]
                """);

        assertEquals("market|ah", placeholders.processString("${root}"));
        assertEquals("market|ah vendre|sell <priceEntry>", placeholders.processString("${root} ${sell} <priceEntry>"));
    }

    @Test
    @DisplayName("Everything that is not a placeholder is left alone")
    void therestOfTheSyntaxIsUntouched() {

        CommandPlaceholders placeholders = placeholders("lang: \"en\"\n");

        assertEquals("ah|hdv admin purge all", placeholders.processString("${root} ${admin} purge all"));
        assertEquals("ah|hdv help [query]", placeholders.processString("${root} ${help} [query]"));
        assertEquals("fauction.sell", placeholders.processString("fauction.sell"));
        assertEquals("{@@fauction.help_description}", placeholders.processString("{@@fauction.help_description}"));
    }

    @Test
    @DisplayName("A placeholder the plugin knows nothing about stays as it is written")
    void anUnknownPlaceholderIsKept() {

        CommandPlaceholders placeholders = placeholders("lang: \"en\"\n");

        // Visibly wrong rather than silently registered under an empty name — and
        // AuctionCommandAnnotationsTest makes sure no such placeholder ever ships.
        assertEquals("ah|hdv ${sel}", placeholders.processString("${root} ${sel}"));
    }

    private CommandPlaceholders placeholders(String yaml) {
        CommandsConfig commandsConfig = new CommandsConfig();
        commandsConfig.load(TestConfigs.of(yaml), LOGGER);
        return new CommandPlaceholders(commandsConfig);
    }
}
