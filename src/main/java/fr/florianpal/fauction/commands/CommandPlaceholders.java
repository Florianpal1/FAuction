package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.configurations.CommandsConfig;
import fr.florianpal.fauction.enums.CommandKey;
import org.incendo.cloud.annotations.string.PropertyReplacingStringProcessor;
import org.incendo.cloud.annotations.string.StringProcessor;

/**
 * Replaces the {@code ${root}}, {@code ${sell}}… of the {@code @Command} annotations with the names
 * the server owner configured, while Cloud parses the annotated container.
 * <p>
 * This is the only thing standing between config.yml and the command tree : the annotations keep
 * describing the commands, and the names they are registered under come from the configuration.
 */
public final class CommandPlaceholders implements StringProcessor {

    private final StringProcessor delegate;

    public CommandPlaceholders(CommandsConfig commandsConfig) {
        this.delegate = new PropertyReplacingStringProcessor(key -> resolve(commandsConfig, key));
    }

    @Override
    public String processString(String input) {
        return delegate.processString(input);
    }

    /**
     * The names of a command the plugin knows about, {@code null} for anything else.
     * <p>
     * {@code null} is what tells Cloud to leave the placeholder as it is written. An unknown
     * placeholder therefore shows up as {@code ${typo}} in the command tree — visibly wrong, and
     * caught by the test walking the annotations — instead of silently registering a command under
     * an empty or broken name.
     */
    private static String resolve(CommandsConfig commandsConfig, String key) {
        return CommandKey.byConfigKey(key)
                .map(commandsConfig::syntax)
                .orElse(null);
    }
}
