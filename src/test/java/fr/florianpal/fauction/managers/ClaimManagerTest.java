package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.enums.ClaimType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimManagerTest {

    @Test
    @DisplayName("A second claim on the same auction is refused while the first one is running")
    void secondClaimIsRefused() {

        ClaimManager claimManager = new ClaimManager();

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));
        assertFalse(claimManager.tryClaim(ClaimType.AUCTION, 42));
    }

    @Test
    @DisplayName("A burst of packets received in the same tick gives a single winner")
    void packetBurstHasASingleWinner() {

        // The server handles every packet of a tick sequentially on the main thread, so the burst
        // of a cheat looks exactly like that : a lot of calls in a row, without any pause.
        ClaimManager claimManager = new ClaimManager();

        int winners = 0;
        for (int packet = 0; packet < 200; packet++) {
            if (claimManager.tryClaim(ClaimType.AUCTION, 42)) {
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
                    if (claimManager.tryClaim(ClaimType.AUCTION, 42)) {
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

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));
        claimManager.release(ClaimType.AUCTION, 42);

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));
    }

    @Test
    @DisplayName("Claiming an auction does not block the other auctions")
    void otherIdsAreNotBlocked() {

        ClaimManager claimManager = new ClaimManager();

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 43));
        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, -42));
    }

    @Test
    @DisplayName("Auctions and expires have separate ids")
    void typesDoNotCollide() {

        ClaimManager claimManager = new ClaimManager();

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));

        assertTrue(claimManager.tryClaim(ClaimType.EXPIRE, 42));
    }

    @Test
    @DisplayName("A claim never released is taken over, so an auction is never frozen")
    void staleClaimIsTakenOver() throws InterruptedException {

        ClaimManager claimManager = new ClaimManager(50);

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));
        assertFalse(claimManager.tryClaim(ClaimType.AUCTION, 42));

        Thread.sleep(80);

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));
    }

    @Test
    @DisplayName("Releasing an auction that was not claimed does nothing")
    void releaseWithoutClaimIsHarmless() {

        ClaimManager claimManager = new ClaimManager();

        claimManager.release(ClaimType.AUCTION, 42);

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));
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
            if (claimManager.tryClaim(seller)) {
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
                    if (claimManager.tryClaim(seller)) {
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

        assertTrue(claimManager.tryClaim(UUID.randomUUID()));
        assertTrue(claimManager.tryClaim(UUID.randomUUID()));
    }

    @Test
    @DisplayName("The player can sell again once the sale is released")
    void releaseAllowsANewSale() {

        ClaimManager claimManager = new ClaimManager();
        UUID seller = UUID.randomUUID();

        assertTrue(claimManager.tryClaim(seller));
        assertFalse(claimManager.tryClaim(seller));

        claimManager.release(seller);

        assertTrue(claimManager.tryClaim(seller));
    }

    @Test
    @DisplayName("A sale never released is taken over, so a player is never stuck")
    void staleSaleIsTakenOver() throws InterruptedException {

        ClaimManager claimManager = new ClaimManager(50);
        UUID seller = UUID.randomUUID();

        assertTrue(claimManager.tryClaim(seller));
        assertFalse(claimManager.tryClaim(seller));

        Thread.sleep(80);

        assertTrue(claimManager.tryClaim(seller));
    }

    @Test
    @DisplayName("Sales and auctions are two separate reservations")
    void salesAndAuctionsDoNotCollide() {

        ClaimManager claimManager = new ClaimManager();
        UUID seller = UUID.randomUUID();

        assertTrue(claimManager.tryClaim(seller));

        assertTrue(claimManager.tryClaim(ClaimType.AUCTION, 42));
        assertTrue(claimManager.tryClaim(ClaimType.EXPIRE, 42));
    }
}