package fr.florianpal.fauction.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatUtil {

    public static String format(String msg) {
        Pattern pattern = Pattern.compile("[{]#[a-fA-F0-9]{6}[}]");

        Matcher match = pattern.matcher(msg);
        while (match.find()) {
            String color = msg.substring(match.start(), match.end());
            String replace = color;
            color = color.replace("{", "");
            color = color.replace("}", "");
            msg = msg.replace(replace, ChatColor.of(color) + "");
            match = pattern.matcher(msg);
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
