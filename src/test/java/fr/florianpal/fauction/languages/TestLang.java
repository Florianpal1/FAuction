package fr.florianpal.fauction.languages;

import dev.dejvokep.boostedyaml.YamlDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * A {@link Lang} loaded from a language file the plugin ships, for the tests reading the messages a
 * sender receives.
 */
public final class TestLang {

    private TestLang() {
    }

    public static Lang of(String code) {
        Lang lang = new Lang();
        try (InputStream in = Objects.requireNonNull(TestLang.class.getResourceAsStream("/lang_" + code + ".yml"))) {
            lang.load(YamlDocument.create(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lang;
    }
}
