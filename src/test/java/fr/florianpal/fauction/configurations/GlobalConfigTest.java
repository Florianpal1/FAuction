package fr.florianpal.fauction.configurations;

import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.objects.SpamLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalConfigTest {

    /**
     * Smallest configuration the plugin accepts, every other value being defaulted.
     */
    private static final String MINIMAL = """
            lang: "en"
            limitations:
              default: 5
            """;

    @Test
    @DisplayName("A minimal configuration falls back to the expected defaults")
    void minimalConfigurationUsesTheDefaults() {

        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of(MINIMAL));

        assertEquals("en", config.getLang());
        assertEquals("AUCTION", config.getDefaultGui());
        assertEquals(CurrencyType.VAULT, config.getCurrencyType());
        assertEquals("0.00", config.getDecimalFormat());
        assertEquals(72000, config.getUpdateCacheEvery());
        assertTrue(config.isFeatureFlippingExpiration());
        assertTrue(config.isFeatureFlippingCacheUpdate());
        assertFalse(config.isFeatureFlippingMoneyFormat());
        assertFalse(config.isFeatureDuplicationHashCodeControl());
        assertEquals(5, config.getLimitations().get("default"));
    }

    @Test
    @DisplayName("The values of the file win over the defaults")
    void fileValuesWin() {

        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of("""
                lang: "fr"
                defaultGui: EXPIRE
                currencyUse: EXPERIENCE
                decimalFormat: "0.###"
                orderBy: "DESC"
                dateFormat: "dd/MM/yyyy"
                remainingDateFormat: "MMM ddd HHh mmm sss"
                cacheUpdate: 1200
                feature-flipping:
                  item-expiration: false
                  cache-update: false
                  money-format: true
                  duplication-hashcode-control: true
                expiration:
                  time: 60
                  checkEvery: 1200
                limitations:
                  default: 5
                  vip: 20
                """));

        assertEquals("fr", config.getLang());
        assertEquals("EXPIRE", config.getDefaultGui());
        assertEquals(CurrencyType.EXPERIENCE, config.getCurrencyType());
        assertEquals("0.###", config.getDecimalFormat());
        assertEquals("DESC", config.getOrderBy());
        assertEquals(1200, config.getUpdateCacheEvery());
        assertEquals(60, config.getTime());
        assertEquals(1200, config.getCheckEvery());
        assertFalse(config.isFeatureFlippingExpiration());
        assertTrue(config.isFeatureFlippingMoneyFormat());
        assertTrue(config.isFeatureDuplicationHashCodeControl());
        assertEquals(20, config.getLimitations().get("vip"));
    }

    @Test
    @DisplayName("The currency scheduler never ends up with a period of zero")
    void currencySchedulerHasAUsablePeriod() {

        // A period of zero makes Bukkit run the task every tick, so the default has to be usable
        // even when the section is missing.
        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of(MINIMAL));

        assertTrue(config.getCheckEveryCurrency() > 0);
        assertTrue(config.getTimeCurrency() > 0);
    }

    @Test
    @DisplayName("The currency section of the file is read")
    void currencySectionIsRead() {

        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of("""
                lang: "en"
                currencyCheck:
                  time: 60
                  checkEvery: 1200
                limitations:
                  default: 5
                """));

        assertEquals(60, config.getTimeCurrency());
        assertEquals(1200, config.getCheckEveryCurrency());
    }

    @Test
    @DisplayName("The anti spam protection is on by default, with the default limits")
    void antiSpamDefaults() {

        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of(MINIMAL));

        assertTrue(config.isSecurityForSpammingPacket());
        assertEquals(3000, config.getSpamMessageCooldown());
        assertEquals(25, config.getSpamLogThreshold());

        assertLimit(8, 4, config.getSpamLimit(SpamAction.INTERACT));
        assertLimit(6, 2, config.getSpamLimit(SpamAction.COMMAND));
        assertLimit(4, 2, config.getSpamLimit(SpamAction.TRANSACTION));
    }

    @Test
    @DisplayName("The anti spam limits of the file are read")
    void antiSpamValuesAreRead() {

        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of("""
                lang: "en"
                securityForSpammingPacket: false
                anti-spam:
                  messageCooldown: 500
                  logThreshold: 0
                  interact:
                    burst: 20
                    perSecond: 10
                  command:
                    burst: 3
                    perSecond: 1.5
                  transaction:
                    burst: 2
                    perSecond: 0.5
                limitations:
                  default: 5
                """));

        assertFalse(config.isSecurityForSpammingPacket());
        assertEquals(500, config.getSpamMessageCooldown());
        assertEquals(0, config.getSpamLogThreshold());

        assertLimit(20, 10, config.getSpamLimit(SpamAction.INTERACT));
        assertLimit(3, 1.5, config.getSpamLimit(SpamAction.COMMAND));
        assertLimit(2, 0.5, config.getSpamLimit(SpamAction.TRANSACTION));
    }

    @Test
    @DisplayName("An anti spam limit of zero is clamped instead of blocking everything")
    void antiSpamLimitsAreClamped() {

        // A burst of zero would refuse every action and a refill of zero would never give a token
        // back, locking the player out until he reconnects.
        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of("""
                lang: "en"
                anti-spam:
                  interact:
                    burst: 0
                    perSecond: 0
                  command:
                    burst: -5
                    perSecond: -5
                limitations:
                  default: 5
                """));

        assertLimit(1, 0.1, config.getSpamLimit(SpamAction.INTERACT));
        assertLimit(1, 0.1, config.getSpamLimit(SpamAction.COMMAND));
    }

    @Test
    @DisplayName("A partial anti spam section keeps the defaults of the missing entries")
    void partialAntiSpamSectionKeepsTheDefaults() {

        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of("""
                lang: "en"
                anti-spam:
                  transaction:
                    burst: 2
                limitations:
                  default: 5
                """));

        assertLimit(2, 2, config.getSpamLimit(SpamAction.TRANSACTION));
        assertLimit(8, 4, config.getSpamLimit(SpamAction.INTERACT));
    }

    @Test
    @DisplayName("A missing limitations section loads as empty instead of throwing")
    void missingLimitationsSectionDoesNotThrow() {

        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of("""
                lang: "en"
                """));

        assertTrue(config.getLimitations().isEmpty());
    }

    @Test
    @DisplayName("Reloading replaces the previous values instead of piling them up")
    void reloadReplacesTheValues() {

        GlobalConfig config = new GlobalConfig();
        config.load(TestConfigs.of("""
                lang: "en"
                anti-spam:
                  interact:
                    burst: 20
                    perSecond: 10
                limitations:
                  default: 5
                  vip: 20
                """));
        config.load(TestConfigs.of(MINIMAL));

        assertLimit(8, 4, config.getSpamLimit(SpamAction.INTERACT));
        assertEquals(1, config.getLimitations().size());
    }

    private void assertLimit(double burst, double perSecond, SpamLimit limit) {
        assertEquals(burst, limit.burst(), "burst");
        assertEquals(perSecond, limit.perSecond(), "perSecond");
    }
}