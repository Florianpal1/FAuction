package fr.florianpal.fauction.utils;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.languages.MessageKeys;
import org.bukkit.entity.Player;

public class MessageUtil {

    /**
     * Sends a message of the plugin to a player. Signature untouched by the move off ACF : the
     * 39 call sites, in six files, do not change.
     * <p>
     * A key absent from the language file sends nothing, which is what ACF did.
     */
    public static void sendMessage(FAuction plugin, Player player, MessageKeys messageKeys, String... replacements) {
        // Legacy string on purpose : the same rendering path as ACF used, so the colour codes and
        // the hex sequences produced by FormatUtil keep behaving exactly as they do today.
        plugin.getLang().message(messageKeys, replacements).ifPresent(player::sendMessage);
    }
}
