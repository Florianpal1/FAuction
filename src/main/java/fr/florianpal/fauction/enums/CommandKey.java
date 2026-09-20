package fr.florianpal.fauction.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A command whose name and aliases the server owner can choose, and the names it carries when they
 * choose nothing.
 * <p>
 * The constant is the identity of the command : the configuration, the {@code ${...}} placeholders
 * of the {@code @Command} annotations and the permissions are all keyed on it, so renaming
 * {@code sell} into {@code vendre} never moves {@code fauction.sell}.
 */
@Getter
public enum CommandKey {

    /**
     * The root of every path. Alone at its level : it can never collide with a subcommand.
     */
    ROOT("root", List.of("ah", "hdv")),

    LIST("list", List.of("list")),

    SEARCH("search", List.of("search")),

    SELL("sell", List.of("sell")),

    EXPIRE("expire", List.of("expire")),

    HELP("help", List.of("help")),

    ADMIN("admin", List.of("admin"));

    /**
     * The key under {@code commands:} in config.yml, which is also the name of the
     * {@code ${...}} placeholder used in the {@code @Command} annotations.
     */
    private final String configKey;

    private final List<String> defaultNames;

    CommandKey(String configKey, List<String> defaultNames) {
        this.configKey = configKey;
        this.defaultNames = defaultNames;
    }

    /**
     * The commands sharing a level with each other : two of them cannot answer to the same name.
     */
    public boolean isSubCommand() {
        return this != ROOT;
    }

    public static Optional<CommandKey> byConfigKey(String configKey) {
        return Arrays.stream(values())
                .filter(key -> key.configKey.equals(configKey))
                .findFirst();
    }
}
