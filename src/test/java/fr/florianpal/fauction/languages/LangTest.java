package fr.florianpal.fauction.languages;

import dev.dejvokep.boostedyaml.YamlDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolution and rendering of a message, without a server nor a file.
 */
class LangTest {

    private static Lang langOf(String yaml) throws IOException {
        Lang lang = new Lang();
        lang.load(YamlDocument.create(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));
        return lang;
    }

    @Test
    @DisplayName("A key present in the file gives its message")
    void presentKeyIsResolved() throws IOException {
        Lang lang = langOf("fauction:\n  auction_open: \"You opened the auction house.\"\n");

        assertEquals(Optional.of("You opened the auction house."), lang.message(MessageKeys.AUCTION_OPEN));
    }

    @Test
    @DisplayName("A key absent from the file sends nothing, as it did under ACF")
    void absentKeySendsNothing() throws IOException {
        Lang lang = langOf("fauction:\n  auction_open: \"You opened the auction house.\"\n");

        // The visible case : DATABASEERROR has no entry in any of the four language files.
        assertTrue(lang.message(MessageKeys.DATABASEERROR).isEmpty());
    }

    @Test
    @DisplayName("Every placeholder of a message is replaced")
    void placeholdersAreReplaced() throws IOException {
        Lang lang = langOf("fauction:\n  auction_add_success: \"Added {item} for {price}.\"\n");

        assertEquals(
                Optional.of("Added a diamond for 12.00."),
                lang.message(MessageKeys.AUCTION_ADD_SUCCESS, "{item}", "a diamond", "{price}", "12.00")
        );
    }

    @Test
    @DisplayName("A replacement without its value is ignored rather than breaking the message")
    void oddReplacementIsIgnored() throws IOException {
        Lang lang = langOf("fauction:\n  auction_add_success: \"Added {item}.\"\n");

        assertEquals(Optional.of("Added {item}."), lang.message(MessageKeys.AUCTION_ADD_SUCCESS, "{item}"));
    }

    @Test
    @DisplayName("Colour codes and hex colours are translated")
    void coloursAreTranslated() throws IOException {
        Lang lang = langOf("fauction:\n  spam: \"&cStop!\"\n  auction_open: \"#FF0000Red\"\n");

        assertEquals(Optional.of("§cStop!"), lang.message(MessageKeys.SPAM));
        assertTrue(lang.message(MessageKeys.AUCTION_OPEN).orElseThrow().endsWith("Red"));
        assertTrue(lang.message(MessageKeys.AUCTION_OPEN).orElseThrow().startsWith("§x"));
    }

    @Test
    @DisplayName("The ACF colour tags of a relocated message become colour codes instead of showing up")
    void acfTagsAreTranslated() throws IOException {
        Lang lang = langOf("fauction:\n  error:\n    invalid_syntax: \"Usage: <c2>{command}</c2> <c3>{syntax}</c3>\"\n");

        assertEquals(
                "Usage: §6/ah§r §csell <price>§r",
                lang.messageOr("fauction.error.invalid_syntax", "fallback",
                        "{command}", "/ah", "{syntax}", "sell <price>")
        );
    }

    @Test
    @DisplayName("A framework message falls back on its default rather than sending nothing")
    void frameworkMessageFallsBack() throws IOException {
        Lang lang = langOf("fauction:\n  auction_open: \"You opened the auction house.\"\n");

        assertEquals("fallback", lang.messageOr("fauction.error.permission_denied", "fallback"));
    }

    @Test
    @DisplayName("The version key is not a message")
    void versionIsNotAMessage() throws IOException {
        Lang lang = langOf("version: 2\nfauction:\n  auction_open: \"Open.\"\n");

        assertTrue(lang.raw("version").isEmpty());
    }
}
