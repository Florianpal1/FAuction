package fr.florianpal.fauction.languages;

import dev.dejvokep.boostedyaml.YamlDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageKeysTest {

    /**
     * No call site sends this one, and no language file translates it. Left in place rather than
     * removed from the enum, but it is the reason an absent key must stay silent.
     */
    private static final MessageKeys NO_TRANSLATION = MessageKeys.DATABASEERROR;

    @Test
    @DisplayName("A key is the prefix and its own name in lower case, as ACF built it")
    void keysKeepTheirAcfRoute() {
        assertEquals("fauction.no_auction", MessageKeys.NO_AUCTION.getKey());
        assertEquals("fauction.buy_auction_target_success", MessageKeys.BUY_AUCTION_TARGET_SUCCESS.getKey());
        assertEquals("fauction.databaseerror", MessageKeys.DATABASEERROR.getKey());

        for (MessageKeys key : MessageKeys.values()) {
            assertEquals("fauction." + key.name().toLowerCase(), key.getKey());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "ru", "zhcn"})
    @DisplayName("Every shipped language file translates every key that is actually sent")
    void shippedFilesTranslateEveryKey(String code) throws IOException {
        Lang lang = new Lang();
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/lang_" + code + ".yml"))) {
            lang.load(YamlDocument.create(in));
        }

        List<String> missing = new ArrayList<>();
        for (MessageKeys key : MessageKeys.values()) {
            if (key != NO_TRANSLATION && lang.raw(key.getKey()).isEmpty()) {
                missing.add(key.getKey());
            }
        }

        assertTrue(missing.isEmpty(), "lang_" + code + ".yml does not translate " + missing);
    }
}
