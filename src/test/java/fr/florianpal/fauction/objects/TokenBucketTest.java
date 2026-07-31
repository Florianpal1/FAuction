package fr.florianpal.fauction.objects;

import fr.florianpal.fauction.enums.SpamAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class



TokenBucketTest {

    private static final SpamLimit LIMIT = new SpamLimit(8, 4);

    @Test
    @DisplayName("The configured burst is allowed at once")
    void burstIsAllowed() {

        TokenBucket bucket = new TokenBucket(LIMIT, 0);

        for (int action = 0; action < 8; action++) {
            assertTrue(bucket.tryConsume(LIMIT, 0), "Action " + action + " should have been allowed");
        }
    }

    @Test
    @DisplayName("A flood of packets in the same millisecond is cut after the burst")
    void floodIsCutAfterTheBurst() {

        TokenBucket bucket = new TokenBucket(LIMIT, 0);

        int allowed = 0;
        for (int packet = 0; packet < 200; packet++) {
            if (bucket.tryConsume(LIMIT, 0)) {
                allowed++;
            }
        }

        assertEquals(8, allowed);
    }

    @Test
    @DisplayName("The bucket refills at the configured rate")
    void refillsAtTheConfiguredRate() {

        TokenBucket bucket = new TokenBucket(LIMIT, 0);

        while (bucket.tryConsume(LIMIT, 0)) {
            // empty the bucket
        }

        // 4 tokens per second, so a token is back after 250 ms.
        assertFalse(bucket.tryConsume(LIMIT, 200));
        assertTrue(bucket.tryConsume(LIMIT, 250));
        assertFalse(bucket.tryConsume(LIMIT, 250));

        // One second later, 4 more actions are available.
        int allowed = 0;
        for (int action = 0; action < 10; action++) {
            if (bucket.tryConsume(LIMIT, 1250)) {
                allowed++;
            }
        }
        assertEquals(4, allowed);
    }

    @Test
    @DisplayName("An idle player never accumulates more than the burst")
    void neverGoesAboveTheBurst() {

        TokenBucket bucket = new TokenBucket(LIMIT, 0);

        // One hour of inactivity would be 14400 tokens without the cap.
        int allowed = 0;
        for (int action = 0; action < 200; action++) {
            if (bucket.tryConsume(LIMIT, 3_600_000)) {
                allowed++;
            }
        }

        assertEquals(8, allowed);
    }

    @Test
    @DisplayName("A player clicking at a human pace is never blocked")
    void humanPaceIsNeverBlocked() {

        TokenBucket bucket = new TokenBucket(LIMIT, 0);

        // A click every 300 ms during five minutes, which is already a fast player.
        for (long now = 0; now <= 300_000; now += 300) {
            assertTrue(bucket.tryConsume(LIMIT, now), "Blocked after " + now + " ms");
        }
    }

    @Test
    @DisplayName("Two clicks 1.9 second apart are not spam")
    void slowClicksAreNotSpam() {

        // Regression : the previous detection compared the seconds of the clicks, so two clicks
        // falling on two consecutive seconds were reported as spam whatever the real delay.
        TokenBucket bucket = new TokenBucket(LIMIT, 0);

        assertTrue(bucket.tryConsume(LIMIT, 999));
        assertTrue(bucket.tryConsume(LIMIT, 2900));
    }

    @Test
    @DisplayName("A long session never ends up blocking the player")
    void longSessionDoesNotDrift() {

        // Regression : the previous detection kept every click of the session, so after a while
        // most of the seconds of a minute were taken and every new click was reported as spam.
        TokenBucket bucket = new TokenBucket(LIMIT, 0);

        for (long now = 0; now <= 3_600_000; now += 500) {
            assertTrue(bucket.tryConsume(LIMIT, now), "Blocked after " + now + " ms");
        }
    }

    @Test
    @DisplayName("A transaction flood is throttled to the configured rate")
    void transactionFloodIsThrottled() {

        SpamLimit limit = SpamAction.TRANSACTION.getDefaultLimit();
        TokenBucket bucket = new TokenBucket(limit, 0);

        // A cheat sending 20 purchases per second during 10 seconds.
        int allowed = 0;
        for (long now = 0; now <= 10_000; now += 50) {
            if (bucket.tryConsume(limit, now)) {
                allowed++;
            }
        }

        // The burst plus the refill rate, and nothing more.
        assertEquals((int) (limit.burst() + limit.perSecond() * 10), allowed);
    }

    @Test
    @DisplayName("Time going backwards does not give free actions")
    void timeGoingBackwardsIsIgnored() {

        TokenBucket bucket = new TokenBucket(LIMIT, 10_000);

        while (bucket.tryConsume(LIMIT, 10_000)) {
            // empty the bucket
        }

        assertFalse(bucket.tryConsume(LIMIT, 0));
    }
}