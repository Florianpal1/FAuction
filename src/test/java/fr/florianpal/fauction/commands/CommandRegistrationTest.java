package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.configurations.CommandsConfig;
import fr.florianpal.fauction.configurations.TestConfigs;
import fr.florianpal.fauction.languages.Lang;
import org.bukkit.Material;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The end of the chain : the real annotations of {@link AuctionCommand}, parsed by the real Cloud,
 * registered under the names of a configuration. Nothing else proves that what config.yml says is
 * what the player types.
 * <p>
 * The manager is a bare Cloud one rather than the Paper one : it needs no server, and the names are
 * decided before Bukkit ever sees them.
 */
class CommandRegistrationTest extends FAuctionTestBase {

    private static final Logger LOGGER = Logger.getLogger("CommandRegistrationTest");

    /**
     * A Cloud manager that registers nowhere. Only the command tree it builds is of interest here.
     */
    private static final class HeadlessManager extends CommandManager<Object> {

        private HeadlessManager() {
            super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());

            // The Bukkit manager brings this one along ; the bare manager does not, and /ah search
            // takes a Material.
            parserRegistry().registerParser(ParserDescriptor.of(
                    (ctx, input) -> ArgumentParseResult.success(
                            Material.valueOf(input.readString().toUpperCase(Locale.ROOT))),
                    Material.class));
        }

        @Override
        public boolean hasPermission(Object sender, String permission) {
            return true;
        }
    }

    @BeforeEach
    void setUpCommand() {
        when(globalConfig.getDecimalFormat()).thenReturn("0.00");
        when(plugin.getLang()).thenReturn(new Lang());
    }

    @Test
    @DisplayName("Without a commands section, the commands answer exactly as they did before")
    void theDefaultsRegisterTheHistoricalCommands() {

        CommandManager<Object> manager = register("lang: \"en\"\n");

        assertEquals(Set.of("ah"), Set.copyOf(manager.rootCommands()));
        assertEquals(Set.of("ah", "hdv"), aliasesOf(manager, "ah"));

        Set<String> paths = paths(manager);
        assertTrue(paths.contains("ah"), paths.toString());
        assertTrue(paths.contains("ah list"), paths.toString());
        assertTrue(paths.contains("ah sell priceEntry"), paths.toString());
        assertTrue(paths.contains("ah search material"), paths.toString());
        assertTrue(paths.contains("ah expire"), paths.toString());
        assertTrue(paths.contains("ah help query"), paths.toString());
        assertTrue(paths.contains("ah admin reload"), paths.toString());
        assertTrue(paths.contains("ah admin purge all"), paths.toString());
        assertTrue(paths.contains("ah admin migrate migrateVersion"), paths.toString());
    }

    @Test
    @DisplayName("The names of config.yml are the names the commands are registered under")
    void configuredNamesReachTheCommandTree() {

        CommandManager<Object> manager = register("""
                commands:
                  root: ["market"]
                  sell: ["vendre", "sell"]
                  admin: ["gestion"]
                """);

        assertEquals(Set.of("market"), Set.copyOf(manager.rootCommands()));

        Set<String> paths = paths(manager);
        assertTrue(paths.contains("market vendre priceEntry"), paths.toString());
        assertTrue(paths.contains("market gestion reload"), paths.toString());

        // The renamed ones are gone, the ones left alone are still there.
        assertFalse(paths.contains("ah sell priceEntry"), paths.toString());
        assertFalse(paths.contains("market sell priceEntry"), paths.toString());
        assertTrue(paths.contains("market list"), paths.toString());

        // An alias answers too, and the main name is the first one of the list.
        assertEquals(Set.of("vendre", "sell"), aliasesOf(manager, "vendre"));
    }

    @Test
    @DisplayName("Renaming a command leaves its permission where it was")
    void permissionsDoNotFollowTheNames() {

        CommandManager<Object> manager = register("""
                commands:
                  root: ["market"]
                  sell: ["vendre"]
                """);

        String sellPermission = manager.commands().stream()
                .filter(command -> path(command.components()).equals("market vendre priceEntry"))
                .map(command -> command.commandPermission().permissionString())
                .findFirst()
                .orElseThrow();

        assertEquals("fauction.sell", sellPermission);
    }

    private CommandManager<Object> register(String yaml) {

        CommandsConfig commandsConfig = new CommandsConfig();
        commandsConfig.load(TestConfigs.of(yaml), LOGGER);

        HeadlessManager manager = new HeadlessManager();
        AnnotationParser<Object> parser = new AnnotationParser<>(manager, Object.class);
        parser.stringProcessor(new CommandPlaceholders(commandsConfig));
        parser.parse(new AuctionCommand(plugin));

        return manager;
    }

    /**
     * Every registered path, as the player types it : the names of the components, arguments
     * included, separated by spaces.
     */
    private static Set<String> paths(CommandManager<Object> manager) {
        return manager.commands().stream()
                .map(command -> path(command.components()))
                .collect(Collectors.toSet());
    }

    private static String path(Iterable<CommandComponent<Object>> components) {
        StringBuilder path = new StringBuilder();
        for (CommandComponent<Object> component : components) {
            if (!path.isEmpty()) {
                path.append(' ');
            }
            path.append(component.name());
        }
        return path.toString();
    }

    private static Set<String> aliasesOf(CommandManager<Object> manager, String componentName) {
        return manager.commands().stream()
                .flatMap(command -> command.components().stream())
                .filter(component -> component.name().equals(componentName))
                .map(component -> Set.copyOf(component.aliases()))
                .findFirst()
                .orElseThrow();
    }
}
