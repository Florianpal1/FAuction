package fr.florianpal.fauction.schedules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidScheduleTest {

    private static final Date NOW = new Date(1_700_000_000_000L);

    @Test
    @DisplayName("A bid ending in the future is not expired")
    void futureBidIsNotExpired() {
        assertFalse(BidSchedule.isExpired(new Date(NOW.getTime() + 1000), NOW));
    }

    @Test
    @DisplayName("A bid is expired the very second it reaches its end date")
    void expiresExactlyOnTheLimit() {
        assertTrue(BidSchedule.isExpired(NOW, NOW));
        assertFalse(BidSchedule.isExpired(new Date(NOW.getTime() + 1), NOW));
    }

    @Test
    @DisplayName("A bid whose end date is in the past is expired")
    void pastBidIsExpired() {
        assertTrue(BidSchedule.isExpired(new Date(NOW.getTime() - TimeUnit.MINUTES.toMillis(1)), NOW));
    }
}
