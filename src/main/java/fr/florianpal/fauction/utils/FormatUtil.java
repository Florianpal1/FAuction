package fr.florianpal.fauction.utils;

import fr.florianpal.fauction.FAuction;
import me.seetch.mlang.MLang;
import me.seetch.mlang.TranslationKeyGenerator;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatUtil {

    /**
     * Rounds a price entered by a player to the precision of the configured money format, when that
     * feature is enabled. Parses back through {@code df} itself (not {@link Double#parseDouble}), so a
     * pattern with a grouping separator (e.g. "#,##0") round-trips instead of throwing.
     */
    public static double applyMoneyFormat(double priceEntry, DecimalFormat df, boolean featureFlippingMoneyFormat) {
        if (!featureFlippingMoneyFormat) {
            return priceEntry;
        }
        try {
            return df.parse(df.format(priceEntry)).doubleValue();
        } catch (ParseException e) {
            return priceEntry;
        }
    }

    public static String format(String msg) {
        Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");

        Matcher match = pattern.matcher(msg);
        while (match.find()) {
            String color = msg.substring(match.start(), match.end());
            msg = msg.replace(color, ChatColor.of(color) + "");
            match = pattern.matcher(msg);
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static String durationFormat(String format, Duration duration) {

        var year = 0;
        var month = 0;
        var day = 0;
        var hour = 0;
        var minute = 0;
        var seconde = 0;

        if (!duration.isNegative()) {

            long baseSecond = duration.getSeconds();
            year = (int) Math.floor((double) duration.getSeconds() / 31557600);
            baseSecond = baseSecond - year * 31557600L;
            month = (int) Math.floor((double) baseSecond / 2629800);
            baseSecond = baseSecond - month * 2629800L;
            day = (int) Math.floor((double) baseSecond / 86400);
            baseSecond = baseSecond - day * 86400L;
            hour = (int) Math.floor((double) baseSecond / 3600);
            baseSecond = baseSecond - hour * 3600L;
            minute = (int) Math.floor((double) baseSecond / 60);
            baseSecond = baseSecond - minute * 60L;
            seconde = (int) baseSecond;
        }

        format = format.replace("yyyy", "" + year);
        format = format.replace("MM", "" + month);
        format = format.replace("dd", "" + day);
        format = format.replace("HH", "" + hour);
        format = format.replace("mm", "" + minute);
        format = format.replace("ss", "" + seconde);

        return format;
    }

    public static String titleItemFormat(ItemStack item, String replacement, String title) {
        return title.replace(replacement, titleItemFormat(item));
    }

    public static String titleItemFormat(ItemStack item) {
        return itemName(item, translate(item));
    }

    /**
     * The name shown to the player: the custom name of the item, otherwise its vanilla translation.
     * MLang gives back the translation key itself when the language file could not be loaded, which
     * would show "block.minecraft.diamond_block" in the message instead of a name, so the material
     * is then written the same way as in the menus.
     */
    static String itemName(ItemStack item, String translation) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        if (translation == null || translation.isEmpty()
                || translation.equals(TranslationKeyGenerator.getItemStackKey(item))) {
            return item.getType().name().replace('_', ' ').toLowerCase();
        }

        return translation;
    }

    private static String translate(ItemStack item) {
        FAuction plugin = FAuction.getApi();
        if (plugin == null) {
            return null;
        }

        MLang mLang = plugin.getMLang();
        return mLang == null ? null : mLang.getItemStackTranslation(item);
    }

    public static String formatLanguageCode(String languageCode) {
        if (languageCode.contains("_")) {
            return languageCode.toLowerCase();
        }

        return switch (languageCode.toLowerCase()) {
            case "ru" -> "ru_ru";
            case "en" -> "en_us";
            case "fr" -> "fr_fr";
            case "zhcn" -> "zh_cn";
            default -> languageCode + "_" + languageCode;
        };
    }

    public static String formatServerVersion() {
        String bukkitVersion = Bukkit.getServer().getBukkitVersion();

        if (bukkitVersion.contains("-")) {
            return bukkitVersion.split("-")[0];
        }

        return bukkitVersion;
    }
}
