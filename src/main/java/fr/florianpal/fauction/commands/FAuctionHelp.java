package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.FAuction;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.AudienceProvider;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The generated help of {@code /ah help}, with its texts taken from the language file.
 * <p>
 * ACF's {@code CommandHelp} needed {@code enableUnstableAPI("help")} and drove its layout from the
 * eight {@code acf-core.help_*} keys. {@link MinecraftHelp} has its own layout — the rendering does
 * change visually — but its texts are still the ones from the four language files.
 */
public final class FAuctionHelp {

    private static final String HELP_PREFIX = "fauction.help.";

    /**
     * The keys of {@link MinecraftHelp}. Their names are the ones of the {@code fauction.help}
     * section of the language files.
     */
    private static final List<String> MESSAGES = List.of(
            MinecraftHelp.MESSAGE_HELP_TITLE,
            MinecraftHelp.MESSAGE_COMMAND,
            MinecraftHelp.MESSAGE_DESCRIPTION,
            MinecraftHelp.MESSAGE_NO_DESCRIPTION,
            MinecraftHelp.MESSAGE_ARGUMENTS,
            MinecraftHelp.MESSAGE_OPTIONAL,
            MinecraftHelp.MESSAGE_SHOWING_RESULTS_FOR_QUERY,
            MinecraftHelp.MESSAGE_NO_RESULTS_FOR_QUERY,
            MinecraftHelp.MESSAGE_AVAILABLE_COMMANDS,
            MinecraftHelp.MESSAGE_CLICK_TO_SHOW_HELP,
            MinecraftHelp.MESSAGE_PAGE_OUT_OF_RANGE,
            MinecraftHelp.MESSAGE_CLICK_FOR_NEXT_PAGE,
            MinecraftHelp.MESSAGE_CLICK_FOR_PREVIOUS_PAGE
    );

    private final MinecraftHelp<CommandSender> help;

    public FAuctionHelp(FAuction plugin, CommandManager<CommandSender> manager) {
        this.help = MinecraftHelp.<CommandSender>builder()
                .commandManager(manager)
                // CommandSender is an Audience on Paper, no adventure platform to bundle.
                .audienceProvider(AudienceProvider.nativeAudience())
                .commandPrefix("/ah help")
                // Close to the yellow / gold / red ACF was configured with.
                .colors(MinecraftHelp.helpColors(
                        NamedTextColor.GOLD,
                        NamedTextColor.YELLOW,
                        NamedTextColor.RED,
                        NamedTextColor.GRAY,
                        NamedTextColor.DARK_GRAY
                ))
                .messages(messages(plugin))
                .build();
    }

    public void query(CommandSender sender, String query) {
        help.queryCommands(query, sender);
    }

    /**
     * The texts of the help, read from the language file. A key left out simply keeps Cloud's own
     * default, so a language file that predates this section still shows a complete help.
     * <p>
     * These texts are plain : the colours come from the theme above, so a colour code would show up
     * as such and is dropped.
     */
    private static Map<String, String> messages(FAuction plugin) {
        Map<String, String> messages = new HashMap<>();
        for (String key : MESSAGES) {
            plugin.getLang().raw(HELP_PREFIX + key)
                    .map(FAuctionHelp::stripColours)
                    .ifPresent(text -> messages.put(key, text));
        }
        return messages;
    }

    private static String stripColours(String text) {
        return text.replaceAll("&[0-9a-fA-Fk-orK-OR]", "")
                .replaceAll("#[a-fA-F0-9]{6}", "")
                .replaceAll("</?c[123]>", "");
    }
}
