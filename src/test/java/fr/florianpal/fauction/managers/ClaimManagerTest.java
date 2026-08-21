package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.enums.ClaimType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimManagerTest {

    @Test
    @DisplayName("A second claim on the same auction is refused while the first one is running")
    void secondClaimIsRefused() {

        ClaimManager claimManager = new ClaimManager();

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));
        assertNotClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));
    }

    @Test
    @DisplayName("A burst of packets received in the same tick gives a single winner")
    void packetBurstHasASingleWinner() {

        // The server handles every packet of a tick sequentially on the main thread, so the burst
        // of a cheat looks exactly like that : a lot of calls in a row, without any pause.
        ClaimManager claimManager = new ClaimManager();

        int winners = 0;
        for (int packet = 0; packet < 200; packet++) {
            if (claimManager.tryClaim(ClaimType.AUCTION, 42) != ClaimManager.NOT_CLAIMED) {
                winners++;
            }
        }

        assertEquals(1, winners);
    }

    @Test
    @DisplayName("Concurrent claims on the same auction give a single winner")
    void concurrentClaimsHaveASingleWinner() throws InterruptedException {

        ClaimManager claimManager = new ClaimManager();

        int threads = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    if (claimManager.tryClaim(ClaimType.AUCTION, 42) != ClaimManager.NOT_CLAIMED) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "The threads did not finish in time");
        assertEquals(1, winners.get());
    }

    @Test
    @DisplayName("The auction can be claimed again once released")
    void releaseAllowsANewClaim() {

        ClaimManager claimManager = new ClaimManager();

        long claim = claimManager.tryClaim(ClaimType.AUCTION, 42);
        assertClaimed(claim);
        claimManager.release(ClaimType.AUCTION, 42, claim);

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));
    }

    @Test
    @DisplayName("Claiming an auction does not block the other auctions")
    void otherIdsAreNotBlocked() {

        ClaimManager claimManager = new ClaimManager();

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 43));
        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, -42));
    }

    @Test
    @DisplayName("Auctions and expires have separate ids")
    void typesDoNotCollide() {

        ClaimManager claimManager = new ClaimManager();

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));

        assertClaimed(claimManager.tryClaim(ClaimType.EXPIRE, 42));
    }

    @Test
    @DisplayName("A claim never released is taken over, so an auction is never frozen")
    void staleClaimIsTakenOver() throws InterruptedException {

        ClaimManager claimManager = new ClaimManager(50);

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));
        assertNotClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));

        Thread.sleep(80);

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));
    }

    @Test
    @DisplayName("A release with a stale token does not drop the claim that took over")
    void releaseWithAStaleTokenDoesNotDropTheNewClaim() throws InterruptedException {

        // The chain that lost the claim to a timeout must not be able to release the new claim of
        // whoever took over meanwhile, otherwise a third attempt could jump in while the second one
        // still believes it owns the auction.
        ClaimManager claimManager = new ClaimManager(50);

        long staleClaim = claimManager.tryClaim(ClaimType.AUCTION, 42);
        assertClaimed(staleClaim);

        Thread.sleep(80);

        long newClaim = claimManager.tryClaim(ClaimType.AUCTION, 42);
        assertClaimed(newClaim);
        assertNotEquals(staleClaim, newClaim);

        // The original (now stale) chain finally finishes and releases its own token.
        claimManager.release(ClaimType.AUCTION, 42, staleClaim);

        // The new claim must still be held : a third attempt is refused.
        assertNotClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));

        // Only the actual owner releasing its own token frees the auction.
        claimManager.release(ClaimType.AUCTION, 42, newClaim);
        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));
    }

    @Test
    @DisplayName("Releasing an auction that was not claimed does nothing")
    void releaseWithoutClaimIsHarmless() {

        ClaimManager claimManager = new ClaimManager();

        claimManager.release(ClaimType.AUCTION, 42, ClaimManager.NOT_CLAIMED);

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));
    }

    @Test
    @DisplayName("A burst of sell packets starts a single sale")
    void sellBurstStartsASingleSale() {

        // The item leaves the inventory only once the sale is accepted, so a single sale in flight
        // means a single auction created for the item held by the player.
        ClaimManager claimManager = new ClaimManager();
        UUID seller = UUID.randomUUID();

        int sales = 0;
        for (int packet = 0; packet < 200; packet++) {
            if (claimManager.tryClaim(seller) != ClaimManager.NOT_CLAIMED) {
                sales++;
            }
        }

        assertEquals(1, sales);
    }

    @Test
    @DisplayName("Concurrent sales of the same player give a single winner")
    void concurrentSalesHaveASingleWinner() throws InterruptedException {

        ClaimManager claimManager = new ClaimManager();
        UUID seller = UUID.randomUUID();

        int threads = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    if (claimManager.tryClaim(seller) != ClaimManager.NOT_CLAIMED) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "The threads did not finish in time");
        assertEquals(1, winners.get());
    }

    @Test
    @DisplayName("A player selling does not block the other players")
    void otherSellersAreNotBlocked() {

        ClaimManager claimManager = new ClaimManager();

        assertClaimed(claimManager.tryClaim(UUID.randomUUID()));
        assertClaimed(claimManager.tryClaim(UUID.randomUUID()));
    }

    @Test
    @DisplayName("The player can sell again once the sale is released")
    void releaseAllowsANewSale() {

        ClaimManager claimManager = new ClaimManager();
        UUID seller = UUID.randomUUID();

        long claim = claimManager.tryClaim(seller);
        assertClaimed(claim);
        assertNotClaimed(claimManager.tryClaim(seller));

        claimManager.release(seller, claim);

        assertClaimed(claimManager.tryClaim(seller));
    }

    @Test
    @DisplayName("A sale never released is taken over, so a player is never stuck")
    void staleSaleIsTakenOver() throws InterruptedException {

        ClaimManager claimManager = new ClaimManager(50);
        UUID seller = UUID.randomUUID();

        assertClaimed(claimManager.tryClaim(seller));
        assertNotClaimed(claimManager.tryClaim(seller));

        Thread.sleep(80);

        assertClaimed(claimManager.tryClaim(seller));
    }

    @Test
    @DisplayName("Sales and auctions are two separate reservations")
    void salesAndAuctionsDoNotCollide() {

        ClaimManager claimManager = new ClaimManager();
        UUID seller = UUID.randomUUID();

        assertClaimed(claimManager.tryClaim(seller));

        assertClaimed(claimManager.tryClaim(ClaimType.AUCTION, 42));
        assertClaimed(claimManager.tryClaim(ClaimType.EXPIRE, 42));
    }

    private void assertClaimed(long token) {
        assertNotEquals(ClaimManager.NOT_CLAIMED, token, "Expected the claim to succeed");
    }

    private void assertNotClaimed(long token) {
        assertEquals(ClaimManager.NOT_CLAIMED, token, "Expected the claim to be refused");
    }
}
