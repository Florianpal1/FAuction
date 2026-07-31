package fr.florianpal.fauction.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatUtilTest {

    @BeforeEach
    void setUpServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("The & colour codes are translated")
    void translatesTheAmpersandCodes() {
        assertEquals("§aHello §lworld", FormatUtil.format("&aHello &lworld"));
    }

    @Test
    @DisplayName("The hexadecimal colours are translated")
    void translatesTheHexadecimalColours() {
        assertEquals(ChatColor.of("#FF0000") + "Red", FormatUtil.format("#FF0000Red"));
    }

    @Test
    @DisplayName("Several hexadecimal colours in the same text are translated")
    void translatesEveryHexadecimalColour() {
        assertEquals(ChatColor.of("#ff0000") + "Red" + ChatColor.of("#00ff00") + "Green",
                FormatUtil.format("#ff0000Red#00ff00Green"));
    }

    @Test
    @DisplayName("A text without any colour is left untouched")
    void leavesAPlainTextUntouched() {
        assertEquals("Diamond Sword", FormatUtil.format("Diamond Sword"));
    }

    @Test
    @DisplayName("The remaining time uses the format of the configuration")
    void formatsTheRemainingTime() {

        // Default format of config.yml, where the letter left after the replacement is the unit.
        String format = "MMM ddd HHh mmm sss";
        Duration duration = Duration.ofDays(3).plusHours(4).plusMinutes(5).plusSeconds(6);

        assertEquals("0M 3d 4h 5m 6s", FormatUtil.durationFormat(format, duration));
    }

    @Test
    @DisplayName("The remaining time splits the months and the years")
    void splitsTheMonthsAndTheYears() {

        Duration duration = Duration.ofSeconds(31557600L + 2629800L);

        assertEquals("1y 1M 0d", FormatUtil.durationFormat("yyyyy MMM ddd", duration));
    }

    @Test
    @DisplayName("An expired auction shows zeros instead of a negative time")
    void showsZerosForANegativeDuration() {
        assertEquals("0M 0d 0h 0m 0s", FormatUtil.durationFormat("MMM ddd HHh mmm sss", Duration.ofSeconds(-120)));
    }

    @Test
    @DisplayName("The language codes are expanded to the Minecraft ones")
    void expandsTheLanguageCodes() {

        assertEquals("fr_fr", FormatUtil.formatLanguageCode("fr"));
        assertEquals("en_us", FormatUtil.formatLanguageCode("en"));
        assertEquals("ru_ru", FormatUtil.formatLanguageCode("ru"));
        assertEquals("zh_cn", FormatUtil.formatLanguageCode("zhcn"));
    }

    @Test
    @DisplayName("An unknown language code is doubled")
    void doublesAnUnknownLanguageCode() {
        assertEquals("de_de", FormatUtil.formatLanguageCode("de"));
    }

    @Test
    @DisplayName("An already complete language code is kept as is")
    void keepsACompleteLanguageCode() {
        assertEquals("pt_br", FormatUtil.formatLanguageCode("pt_br"));
    }

    @Test
    @DisplayName("The language code is case insensitive")
    void languageCodeIsCaseInsensitive() {
        assertEquals("fr_fr", FormatUtil.formatLanguageCode("FR"));
        assertEquals("pt_br", FormatUtil.formatLanguageCode("pt_BR"));
    }

    @Test
    @DisplayName("The custom name of the item wins over the translation")
    void keepsTheCustomNameOfTheItem() {

        ItemStack item = new ItemStack(Material.DIAMOND_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bBloc légendaire");
        item.setItemMeta(meta);

        assertEquals("§bBloc légendaire", FormatUtil.itemName(item, "Bloc de diamant"));
    }

    @Test
    @DisplayName("An item without a custom name uses its vanilla translation")
    void usesTheVanillaTranslation() {
        assertEquals("Bloc de diamant", FormatUtil.itemName(new ItemStack(Material.DIAMOND_BLOCK), "Bloc de diamant"));
    }

    @Test
    @DisplayName("An untranslated item shows its material and not the translation key")
    void fallsBackOnTheMaterialWhenTheTranslationIsMissing() {

        ItemStack item = new ItemStack(Material.DIAMOND_BLOCK);

        // MLang gives the key back when the language file of the version could not be downloaded.
        assertEquals("diamond block", FormatUtil.itemName(item, "block.minecraft.diamond_block"));
        assertEquals("diamond block", FormatUtil.itemName(item, null));
        assertEquals("diamond block", FormatUtil.itemName(item, ""));
    }
}