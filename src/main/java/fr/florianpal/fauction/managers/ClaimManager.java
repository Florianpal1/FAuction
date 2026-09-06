package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.enums.ClaimType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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

    /**
     * Returned by tryClaim() when someone else already holds the claim.
     */
    public static final long NOT_CLAIMED = -1L;

    private final Map<Long, Long> claims = new ConcurrentHashMap<>();

    /**
     * Sales in flight, a player being allowed to sell only one item at a time.
     */
    private final Map<UUID, Long> playerClaims = new ConcurrentHashMap<>();

    /**
     * Bids being started, kept separate from playerClaims so starting a bid never blocks on (or gets
     * blocked by) a classic sale in flight for the same player, and vice versa.
     */
    private final Map<UUID, Long> playerBidClaims = new ConcurrentHashMap<>();

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
     * @return a token to give back to release(), or NOT_CLAIMED if someone else already holds it.
     */
    public long tryClaim(ClaimType type, int id) {
        return tryClaim(claims, key(type, id));
    }

    /**
     * Releases the claim, but only if it is still the one identified by token. A claim taken over by
     * someone else after a timeout is never released by the chain that lost it, which would otherwise
     * drop a claim it no longer owns.
     */
    public void release(ClaimType type, int id, long token) {
        release(claims, key(type, id), token);
    }

    /**
     * Reserves the sale of a player. The item leaving the inventory and the auction being created
     * are not a single operation, so a second sale started meanwhile could work on an item that is
     * already sold.
     *
     * @return a token to give back to release(), or NOT_CLAIMED if a sale of this player is already
     * in flight.
     */
    public long tryClaim(UUID playerUUID) {
        return tryClaim(playerClaims, playerUUID);
    }

    public void release(UUID playerUUID, long token) {
        release(playerClaims, playerUUID, token);
    }

    /**
     * Reserves the start of a bid for a player, the same way tryClaim(UUID) does for a classic sale.
     */
    public long tryClaimBid(UUID playerUUID) {
        return tryClaim(playerBidClaims, playerUUID);
    }

    public void releaseBid(UUID playerUUID, long token) {
        release(playerBidClaims, playerUUID, token);
    }

    private <K> long tryClaim(Map<K, Long> reservations, K key) {

        long now = now();
        AtomicLong acquired = new AtomicLong(NOT_CLAIMED);

        reservations.compute(key, (k, claimedAt) -> {
            if (claimedAt == null || now - claimedAt > claimTimeout) {
                acquired.set(now);
                return now;
            }
            return claimedAt;
        });

        return acquired.get();
    }

    private <K> void release(Map<K, Long> reservations, K key, long token) {
        if (token == NOT_CLAIMED) {
            return;
        }
        // Compare-and-remove : only removes the entry if it is still the one this token was issued
        // for, so a claim already taken over by someone else survives this call.
        reservations.remove(key, token);
    }

    private long key(ClaimType type, int id) {
        return ((long) type.ordinal() << 32) | (id & 0xFFFFFFFFL);
    }

    private long now() {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
