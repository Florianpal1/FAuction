package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.commands.AuctionCommand;
import fr.florianpal.fauction.commands.FAuctionHelp;
import fr.florianpal.fauction.enums.MigrateVersion;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.utils.FormatUtil;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.bukkit.BukkitCommandManager;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.CommandExecutionException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.handling.ExceptionContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.minecraft.extras.caption.ComponentCaptionFormatter;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import java.util.logging.Level;

/**
 * The command framework of the plugin : a facade over Cloud.
 * <p>
 * {@link LegacyPaperCommandManager} rather than the modern {@code PaperCommandManager} : the modern
 * one speaks Paper's Brigadier API and would require Paper 1.20.6 or newer, while the plugin
 * announces 1.13+. Both register Cloud's Folia-aware preprocessor, which is what the migration was
 * after — the handler runs on the region thread of the player, and the suggestions never go through
 * the Bukkit scheduler.
 */
public class CommandManager {

    /**
     * The prefix of the language keys carrying the messages of the framework itself. They used to
     * live under {@code acf-core}, and are relocated by the language updater.
     */
    private static final String ERROR_PREFIX = "fauction.error.";

    private final FAuction plugin;

    private final LegacyPaperCommandManager<CommandSender> manager;

    private final AnnotationParser<CommandSender> annotationParser;

    public CommandManager(FAuction plugin) {
        this.plugin = plugin;

        // simpleCoordinator : the handler starts on the calling thread, so on the region thread of
        // the player under Folia. The 13 handlers touch the inventory of the player and open
        // inventories ; an async coordinator would drop them on an arbitrary thread and every one of
        // them would have to jump back. Going asynchronous stays explicit, through newChain().
        this.manager = LegacyPaperCommandManager.createNative(plugin, ExecutionCoordinator.simpleCoordinator());

        registerBrigadier();
        registerExceptionHandlers();

        this.annotationParser = new AnnotationParser<>(manager, CommandSender.class);

        // ACF resolved {@@key} against the language file. Cloud knows nothing of that syntax, so the
        // resolution happens here : the four language files keep driving the help.
        this.annotationParser.descriptionMapper(this::describe);
    }

    /**
     * Brigadier gives non-blocking suggestions, computed without the Bukkit scheduler. Paper's
     * asynchronous completion API is deliberately not used as a fallback : it does go through that
     * scheduler, which is the very thing Folia forbids. Without Brigadier the suggestions are simply
     * computed synchronously, as they always were.
     */
    private void registerBrigadier() {
        if (!manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            plugin.getLogger().info("Brigadier is not available on this server, suggestions will be synchronous.");
            return;
        }

        try {
            manager.registerBrigadier();
        } catch (BukkitCommandManager.BrigadierInitializationException e) {
            plugin.getLogger().warning("Could not enable Brigadier, suggestions will be synchronous : " + e.getMessage());
        }
    }

    /**
     * Registers the commands of an annotated container.
     */
    public void register(Object commandContainer) {
        annotationParser.parse(commandContainer);
    }

    /**
     * Shows the generated help. Built on demand : the help reads its texts from the language file,
     * and {@code /ah admin reload} reloads those.
     */
    public void help(CommandSender sender, String query) {
        new FAuctionHelp(plugin, manager).query(sender, query);
    }

    /**
     * Resolves a {@code {@@route}} description against the language file, so the help stays
     * translated in the four languages. Resolved once, when the commands are registered — as ACF did.
     */
    private Description describe(String description) {
        if (!description.startsWith("{@@") || !description.endsWith("}")) {
            return Description.of(description);
        }

        String route = description.substring(3, description.length() - 1);
        return Description.of(plugin.getLang().raw(route).orElse(route));
    }

    /**
     * The messages of the framework, in the language of the server. Without this, a French player
     * without permission would get Cloud's English default instead of the translation the plugin has
     * always shown.
     */
    private void registerExceptionHandlers() {
        MinecraftExceptionHandler.<CommandSender>createNative()
                .handler(NoPermissionException.class,
                        (formatter, ctx) -> error("permission_denied"))
                .handler(InvalidCommandSenderException.class,
                        (formatter, ctx) -> error("not_allowed_on_console"))
                .handler(InvalidSyntaxException.class, (formatter, ctx) -> {
                    String syntax = ctx.exception().correctSyntax();
                    int space = syntax.indexOf(' ');
                    return error("invalid_syntax",
                            "{command}", "/" + (space < 0 ? syntax : syntax.substring(0, space)),
                            "{syntax}", space < 0 ? "" : syntax.substring(space + 1));
                })
                .handler(ArgumentParseException.class, this::parseFailure)
                .handler(CommandExecutionException.class, (formatter, ctx) -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "Error while performing " + ctx.context().rawInput().input(), ctx.exception().getCause());
                    return error("internal");
                })
                .registerTo(manager);
    }

    /**
     * All the parsing failures arrive as a single exception type, so the cause is what tells them
     * apart. The two custom parsers of {@code AuctionCommand} are mapped onto the messages the player
     * used to get from the handler itself, so nothing changes on their side.
     */
    private ComponentLike parseFailure(
            ComponentCaptionFormatter<CommandSender> formatter,
            ExceptionContext<CommandSender, ArgumentParseException> ctx
    ) {
        Throwable cause = ctx.exception().getCause();

        if (cause instanceof AuctionCommand.InvalidPriceException) {
            // Same message as the price < 0 check of the handler : a refused price reads the same
            // way as before, whether it is negative, NaN or infinite.
            return message(MessageKeys.NEGATIVE_PRICE);
        }

        if (cause instanceof AuctionCommand.NotANumberException notANumber) {
            return error("must_be_a_number", "{num}", notANumber.getInput());
        }

        if (cause instanceof AuctionCommand.UnknownMigrateVersionException unknownVersion) {
            return error("unknown_migrate_version",
                    "{version}", unknownVersion.getInput(),
                    "{versions}", String.join(", ", MigrateVersion.ids()));
        }

        // Anything else (an unknown material, for instance) keeps Cloud's own wording, which names
        // what it could not parse.
        return MinecraftExceptionHandler.<CommandSender>createDefaultArgumentParsingHandler()
                .message(formatter, ctx);
    }

    private ComponentLike error(String key, String... replacements) {
        return FormatUtil.component(plugin.getLang().messageOr(ERROR_PREFIX + key, key, replacements));
    }

    /**
     * A message of the plugin as a component, or {@code null} — which Cloud reads as "send nothing",
     * the same silence a key absent from the language file gets everywhere else.
     */
    private ComponentLike message(MessageKeys key) {
        return plugin.getLang().message(key).map(FormatUtil::component).orElse(null);
    }
}
