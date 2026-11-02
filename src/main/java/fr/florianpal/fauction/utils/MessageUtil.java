package fr.florianpal.fauction.utils;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.languages.MessageKeys;
import org.bukkit.command.CommandSender;

public class MessageUtil {

    /**
     * Sends a message of the plugin to a player, or to the console for a command it can run too.
     * Taking a {@link CommandSender} keeps the 39 player call sites, in six files, unchanged.
     * <p>
     * A key absent from the language file sends nothing, which is what ACF did.
     */
    public static void sendMessage(FAuction plugin, CommandSender sender, MessageKeys messageKeys, String... replacements) {
        // Legacy string on purpose : the same rendering path as ACF used, so the colour codes and
        // the hex sequences produced by FormatUtil keep behaving exactly as they do today.
        plugin.getLang().message(messageKeys, replacements).ifPresent(sender::sendMessage);
    }
}
