package fr.florianpal.fauction.schedules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpireScheduleTest {

    /**
     * Default lifetime of config.yml, one hour.
     */
    private static final int ONE_HOUR = 3600;

    private static final Date NOW = new Date(1_700_000_000_000L);

    @Test
    @DisplayName("An auction posted a minute ago is not expired")
    void freshAuctionIsNotExpired() {
        assertFalse(ExpireSchedule.isExpired(minutesAgo(1), ONE_HOUR, NOW));
    }

    @Test
    @DisplayName("An auction older than the lifetime is expired")
    void oldAuctionIsExpired() {
        assertFalse(ExpireSchedule.isExpired(minutesAgo(59), ONE_HOUR, NOW));
        assertTrue(ExpireSchedule.isExpired(minutesAgo(61), ONE_HOUR, NOW));
    }

    @Test
    @DisplayName("An auction is expired the very second it reaches its lifetime")
    void expiresExactlyOnTheLimit() {

        Date exactly = new Date(NOW.getTime() - TimeUnit.SECONDS.toMillis(ONE_HOUR));

        assertTrue(ExpireSchedule.isExpired(exactly, ONE_HOUR, NOW));
        assertFalse(ExpireSchedule.isExpired(new Date(exactly.getTime() + 1), ONE_HOUR, NOW));
    }

    @Test
    @DisplayName("A lifetime of zero expires the auctions right away")
    void zeroLifetimeExpiresImmediately() {
        assertTrue(ExpireSchedule.isExpired(NOW, 0, NOW));
    }

    @Test
    @DisplayName("An auction posted in the future is not expired")
    void futureAuctionIsNotExpired() {
        assertFalse(ExpireSchedule.isExpired(new Date(NOW.getTime() + 1000), ONE_HOUR, NOW));
    }

    @Test
    @DisplayName("A long lifetime is handled without overflowing")
    void longLifetimeDoesNotOverflow() {

        // 30 days, a value a server can legitimately set.
        int oneMonth = (int) TimeUnit.DAYS.toSeconds(30);

        assertFalse(ExpireSchedule.isExpired(daysAgo(29), oneMonth, NOW));
        assertTrue(ExpireSchedule.isExpired(daysAgo(31), oneMonth, NOW));
    }

    private Date minutesAgo(int minutes) {
        return new Date(NOW.getTime() - TimeUnit.MINUTES.toMillis(minutes));
    }

    private Date daysAgo(int days) {
        return new Date(NOW.getTime() - TimeUnit.DAYS.toMillis(days));
    }
}