package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.enums.CommandKey;
import org.incendo.cloud.annotations.Command;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The safety net of the mechanism : a placeholder that matches no {@link CommandKey} is replaced by
 * nothing at all, and the command silently disappears from the server. Here it fails the build.
 */
class AuctionCommandAnnotationsTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\S+)}");

    @Test
    @DisplayName("Every placeholder of the command paths is a command the configuration knows")
    void everyPlaceholderIsAKnownCommand() {

        Set<String> used = placeholders();

        assertTrue(used.size() > 1, "The command paths should be built on placeholders, found " + used);

        for (String placeholder : used) {
            assertTrue(CommandKey.byConfigKey(placeholder).isPresent(),
                    "${" + placeholder + "} matches no CommandKey : the command would be registered under an "
                            + "unusable name. Add the key, or fix the annotation.");
        }
    }

    @Test
    @DisplayName("Every configurable command is actually used by a command path")
    void everyKnownCommandIsUsed() {

        Set<String> used = placeholders();

        for (CommandKey key : CommandKey.values()) {
            assertTrue(used.contains(key.getConfigKey()),
                    "commands." + key.getConfigKey() + " is offered in config.yml but no command path uses "
                            + "${" + key.getConfigKey() + "} : renaming it would do nothing.");
        }
    }

    @Test
    @DisplayName("No command path carries a name in clear, they all go through the configuration")
    void noPathIsHardcoded() {

        for (String path : paths()) {
            assertEquals('$', path.charAt(0),
                    "The path \"" + path + "\" starts with a name written in clear : it could not be renamed.");
        }
    }

    private static Set<String> placeholders() {
        Set<String> placeholders = new LinkedHashSet<>();
        for (String path : paths()) {
            Matcher matcher = PLACEHOLDER.matcher(path);
            while (matcher.find()) {
                placeholders.add(matcher.group(1));
            }
        }
        return placeholders;
    }

    private static Set<String> paths() {
        Set<String> paths = new LinkedHashSet<>();
        for (Method method : AuctionCommand.class.getDeclaredMethods()) {
            Arrays.stream(method.getAnnotationsByType(Command.class))
                    .map(Command::value)
                    .forEach(paths::add);
        }
        return paths;
    }
}
