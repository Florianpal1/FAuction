package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.enums.ClaimType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reserves an auction before any asynchronous work is scheduled for it.
 * <p>
 * Every packet of a tick is handled sequentially on the main thread, so a click that reserves the
 * auction before scheduling its chain makes all the other clicks of the same tick bounce
 * immediately : they cannot reach the database check anymore, hence they cannot pass it while the
 * first one is still running.
 * <p>
 * This is the first barrier only. The delete of the row stays the authority : it is the one telling
 * who really owns the item when several servers share the same database.
 */
public class ClaimManager {

    /**
     * A claim never released, because a chain died on an exception, is taken over after that delay (ms).
     */
    private static final long CLAIM_TIMEOUT = 10_000L;

    private final Map<Long, Long> claims = new ConcurrentHashMap<>();

    /**
     * Sales in flight, a player being allowed to sell only one item at a time.
     */
    private final Map<UUID, Long> playerClaims = new ConcurrentHashMap<>();

    /**
     * Origin of the monotonic clock, so a system time change cannot freeze an auction.
     */
    private final long startNano = System.nanoTime();

    private final long claimTimeout;

    public ClaimManager() {
        this(CLAIM_TIMEOUT);
    }

    ClaimManager(long claimTimeout) {
        this.claimTimeout = claimTimeout;
    }

    /**
     * @return true if the caller owns the auction and must release it once done.
     */
    public boolean tryClaim(ClaimType type, int id) {
        return tryClaim(claims, key(type, id));
    }

    public void release(ClaimType type, int id) {
        claims.remove(key(type, id));
    }

    /**
     * Reserves the sale of a player. The item leaving the inventory and the auction being created
     * are not a single operation, so a second sale started meanwhile could work on an item that is
     * already sold.
     *
     * @return true if the caller owns the sale and must release it once done.
     */
    public boolean tryClaim(UUID playerUUID) {
        return tryClaim(playerClaims, playerUUID);
    }

    public void release(UUID playerUUID) {
        playerClaims.remove(playerUUID);
    }

    private <K> boolean tryClaim(Map<K, Long> reservations, K key) {

        long now = now();
        AtomicBoolean acquired = new AtomicBoolean(false);

        reservations.compute(key, (k, claimedAt) -> {
            if (claimedAt == null || now - claimedAt > claimTimeout) {
                acquired.set(true);
                return now;
            }
            return claimedAt;
        });

        return acquired.get();
    }

    private long key(ClaimType type, int id) {
        return ((long) type.ordinal() << 32) | (id & 0xFFFFFFFFL);
    }

    private long now() {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
