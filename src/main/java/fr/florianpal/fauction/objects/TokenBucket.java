package fr.florianpal.fauction.objects;

/**
 * Token bucket used to rate limit the actions of a player.
 * <p>
 * The bucket starts full, every action consumes a token and the bucket refills at a fixed rate. A
 * human clicking by bursts stays inside the burst allowance, while a client sending dozens of
 * packets per second empties the bucket at once and gets throttled to the refill rate.
 * <p>
 * The clock is given by the caller, so the behaviour does not depend on the wall clock.
 */
public class TokenBucket {

    private double tokens;

    private long lastRefill;

    public TokenBucket(SpamLimit limit, long now) {
        this.tokens = limit.burst();
        this.lastRefill = now;
    }

    /**
     * @param now current time in milliseconds, from a monotonic clock.
     * @return true if the action is allowed.
     */
    public boolean tryConsume(SpamLimit limit, long now) {

        double elapsedSeconds = Math.max(0, now - lastRefill) / 1000.0;
        lastRefill = now;
        tokens = Math.min(limit.burst(), tokens + (elapsedSeconds * limit.perSecond()));

        if (tokens < 1.0) {
            return false;
        }
        tokens -= 1.0;
        return true;
    }
}
