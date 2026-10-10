package fr.florianpal.fauction.testing;

import fr.florianpal.fauction.api.importer.testing.TestImportContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;

/**
 * The source files of the import tests, from {@code src/test/resources/importers/}, with their
 * {@code ${TOKEN}} replaced : the items, encoded at the time of the test ({@link TestItems}), and the
 * dates.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static String load(String resource, Map<String, String> tokens) throws IOException {
        String content;
        try (InputStream in = Objects.requireNonNull(Fixtures.class.getResourceAsStream("/importers/" + resource), resource)) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            content = content.replace("${" + token.getKey() + "}", token.getValue());
        }
        if (content.contains("${")) {
            throw new IllegalStateException("Token left unreplaced in " + resource + " : "
                    + content.substring(content.indexOf("${"), Math.min(content.length(), content.indexOf("${") + 30)));
        }
        return content;
    }

    /**
     * Writes {@code resource}, tokens replaced, to {@code target}, last modified at
     * {@link TestImportContext#NOW} : the modules evaluate the sales when the source last saved
     * its files.
     */
    public static File install(String resource, Map<String, String> tokens, File target) throws IOException {
        Files.createDirectories(target.getParentFile().toPath());
        Files.writeString(target.toPath(), load(resource, tokens), StandardCharsets.UTF_8);
        if (!target.setLastModified(TestImportContext.NOW.toEpochMilli())) {
            throw new IOException("Could not date " + target);
        }
        return target;
    }
}
