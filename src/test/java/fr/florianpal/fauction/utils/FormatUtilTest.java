package fr.florianpal.fauction.utils;

import net.md_5.bungee.api.ChatColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatUtilTest {

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
    }
}