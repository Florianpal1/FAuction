package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportOptionsTest {

    @Test
    @DisplayName("No option : every type, a real import, bids converted, sales in another currency refused")
    void defaults() {
        assertEquals(ImportOptions.DEFAULTS, ImportOptions.parse(null));
        assertEquals(ImportOptions.DEFAULTS, ImportOptions.parse("   "));
    }

    @Test
    @DisplayName("Every option, in both spellings")
    void everyOption() {
        ImportOptions options = ImportOptions.parse("--types auction,pending --dry-run --apply-limits --expired-to-expires "
                + "--force-reimport --other-currency=expire --bids skip --opt mode=runtime --opt=Recover-Orphans=true");

        assertEquals(Set.of(ImportDataType.AUCTION, ImportDataType.PENDING_CURRENCY), options.types());
        assertTrue(options.dryRun());
        assertTrue(options.applyLimits());
        assertTrue(options.expiredToExpires());
        assertTrue(options.forceReimport());
        assertEquals(ImportOptions.OtherCurrency.EXPIRE, options.otherCurrency());
        assertFalse(options.convertBids());
        assertEquals(Map.of("mode", "runtime", "recover-orphans", "true"), options.moduleOptions());
        assertFalse(options.confirm());
    }

    @Test
    @DisplayName("confirm is a word, not an option, and is dropped from what is compared")
    void confirm() {
        ImportOptions options = ImportOptions.parse("confirm");
        assertTrue(options.confirm());
        assertEquals(ImportOptions.DEFAULTS, options.withoutConfirm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--unknown", "--types", "--types bid", "--types ,", "--other-currency maybe", "--bids keep",
            "--opt novalue", "--opt =x", "--dry-run=yes", "stray"})
    @DisplayName("A faulty option is refused with a message naming it")
    void faultyOptions(String input) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ImportOptions.parse(input));
        assertFalse(error.getMessage().isBlank());
    }

    @Test
    @DisplayName("Confirmation : same sender, same module, within 30 s, once")
    void confirmations() {
        MutableClock clock = new MutableClock();
        ImportConfirmations confirmations = new ImportConfirmations(clock);
        ImportOptions options = ImportOptions.parse("--types auction");

        // Nothing asked.
        assertTrue(confirmations.confirm("console:CONSOLE", "x").isEmpty());

        confirmations.request("console:CONSOLE", "x", options);
        assertTrue(confirmations.confirm("player", "x").isEmpty(), "another sender cannot confirm");
        assertEquals(options, confirmations.confirm("console:CONSOLE", "x").orElseThrow());
        assertTrue(confirmations.confirm("console:CONSOLE", "x").isEmpty(), "used once");

        confirmations.request("console:CONSOLE", "x", options);
        assertTrue(confirmations.confirm("console:CONSOLE", "other").isEmpty(), "another module is not confirmed");
        assertTrue(confirmations.confirm("console:CONSOLE", "x").isEmpty(), "the wrong confirmation consumed the request");

        confirmations.request("console:CONSOLE", "x", options);
        clock.now = clock.now.plus(ImportConfirmations.DELAY).plusSeconds(1);
        assertTrue(confirmations.confirm("console:CONSOLE", "x").isEmpty(), "too late");

        confirmations.request("console:CONSOLE", "x", options);
        clock.now = clock.now.plus(Duration.ofSeconds(29));
        assertTrue(confirmations.confirm("console:CONSOLE", "x").isPresent(), "29 s is in time");
    }

    static final class MutableClock extends Clock {

        Instant now = Instant.parse("2026-10-10T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
