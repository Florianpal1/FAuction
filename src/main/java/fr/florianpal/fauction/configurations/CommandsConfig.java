package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.enums.CommandKey;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The names and aliases of the commands, read from the {@code commands:} section of config.yml.
 * <p>
 * The first name of a list is the one the help and the error messages show ; the others are
 * aliases. A section left out, or an entry the framework could not accept, falls back on the
 * defaults of {@link CommandKey} : a mistake in the configuration never keeps the plugin from
 * starting, and never leaves a command unreachable.
 * <p>
 * Nothing here touches the permissions : they stay keyed on the command, not on its name.
 */
public class CommandsConfig {

    static final String SECTION = "commands";

    /**
     * What a command name may not contain. The spaces and {@code |} would be read as a new part of
     * the path by Cloud, the brackets as an argument, {@code :} is the separator Bukkit uses for its
     * own {@code /plugin:command} form, and {@code $}, <code>{</code>, <code>}</code> and
     * {@code \} would be read as a group reference while the placeholders are replaced.
     */
    private static final String FORBIDDEN = "|<>[]{}:/\\$\"'";

    private final Map<CommandKey, List<String>> names = new EnumMap<>(CommandKey.class);

    public CommandsConfig() {
        names.putAll(defaults());
    }

    /**
     * The names of a command, the first one being the main one.
     */
    public List<String> names(CommandKey key) {
        return names.get(key);
    }

    /**
     * The name shown to the player : the first one configured.
     */
    public String primary(CommandKey key) {
        return names.get(key).get(0);
    }

    /**
     * The literal Cloud expects, {@code ah|hdv} : the main name then its aliases.
     */
    public String syntax(CommandKey key) {
        return String.join("|", names.get(key));
    }

    /**
     * A copy of every name, to compare two loads — the commands are registered once, at startup,
     * so {@code /ah admin reload} can only warn that they changed.
     */
    public Map<CommandKey, List<String>> snapshot() {
        return new EnumMap<>(names);
    }

    public void load(YamlDocument config, Logger logger) {

        Map<CommandKey, List<String>> loaded = new EnumMap<>(CommandKey.class);
        for (CommandKey key : CommandKey.values()) {
            loaded.put(key, read(config, key, logger));
        }

        // Two commands of the same level answering to the same name is refused by Cloud when they
        // are registered, which would keep the plugin from starting. Falling back on the defaults of
        // the offending key is not enough : that default may be the very name the other key took.
        // The whole section goes back to the defaults, so the state is one the owner can reason
        // about rather than a half-renamed set of commands.
        if (hasCollision(loaded, logger)) {
            names.putAll(defaults());
            return;
        }

        names.putAll(loaded);
    }

    /**
     * The names configured for one command, or its defaults if the configuration has nothing usable
     * to say about it.
     */
    private List<String> read(YamlDocument config, CommandKey key, Logger logger) {

        String path = SECTION + "." + key.getConfigKey();

        if (!config.contains(path)) {
            return key.getDefaultNames();
        }

        Optional<List<String>> configured = config.getOptionalStringList(path);
        if (configured.isEmpty()) {
            logger.warning("commands." + key.getConfigKey() + " must be a list of names, for instance "
                    + "[\"" + String.join("\", \"", key.getDefaultNames()) + "\"] ; the default names are used.");
            return key.getDefaultNames();
        }

        // A set : the same name twice is a duplicate literal for Cloud, and the order is the one of
        // the file — the first name is the main one.
        LinkedHashSet<String> accepted = new LinkedHashSet<>();
        for (String name : configured.get()) {
            normalize(name, key, logger).ifPresent(accepted::add);
        }

        if (accepted.isEmpty()) {
            logger.warning("commands." + key.getConfigKey() + " has no usable name left ; the default names are used.");
            return key.getDefaultNames();
        }

        return List.copyOf(accepted);
    }

    /**
     * One name, trimmed and lowercased. Bukkit lowercases the label it dispatches on, while the
     * literals of Cloud are compared as they are written : a name left in uppercase would simply
     * never match.
     */
    private Optional<String> normalize(String name, CommandKey key, Logger logger) {

        if (name == null || name.isBlank()) {
            logger.warning("An empty name of commands." + key.getConfigKey() + " is ignored.");
            return Optional.empty();
        }

        String normalized = name.trim().toLowerCase(Locale.ROOT);

        for (char forbidden : FORBIDDEN.toCharArray()) {
            if (normalized.indexOf(forbidden) >= 0) {
                logger.warning("The name \"" + name + "\" of commands." + key.getConfigKey()
                        + " contains the forbidden character '" + forbidden + "' and is ignored.");
                return Optional.empty();
            }
        }

        // A name in several words would be read as several parts of the path by Cloud.
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            logger.warning("The name \"" + name + "\" of commands." + key.getConfigKey()
                    + " contains a space and is ignored.");
            return Optional.empty();
        }

        return Optional.of(normalized);
    }

    /**
     * Two subcommands answering to the same name. The root is alone at its level, so it is left out.
     */
    private boolean hasCollision(Map<CommandKey, List<String>> loaded, Logger logger) {

        Map<String, CommandKey> taken = new HashMap<>();
        boolean collision = false;

        for (CommandKey key : CommandKey.values()) {
            if (!key.isSubCommand()) {
                continue;
            }

            for (String name : loaded.get(key)) {
                CommandKey owner = taken.putIfAbsent(name, key);
                if (owner != null) {
                    logger.severe("commands." + key.getConfigKey() + " and commands." + owner.getConfigKey()
                            + " both answer to \"" + name + "\" ; the whole commands section is ignored and the "
                            + "default names are used.");
                    collision = true;
                }
            }
        }

        return collision;
    }

    private static Map<CommandKey, List<String>> defaults() {
        Map<CommandKey, List<String>> defaults = new EnumMap<>(CommandKey.class);
        for (CommandKey key : CommandKey.values()) {
            defaults.put(key, key.getDefaultNames());
        }
        return defaults;
    }
}
