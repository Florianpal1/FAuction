package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Bid;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidCommandManagerTest extends FAuctionTestBase {

    private static final UUID SECOND_BIDDER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    @DisplayName("Ending a bid reserves it and gives it to the finalizer")
    void endingReservesTheBid() {

        ItemStack sword = namedItem(Material.DIAMOND_SWORD, 1, "Excalibur");
        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0, 250.0, BUYER, "Buyer", sword))));
        BidCommandManager manager = new BidCommandManager(plugin);

        Bid ended = manager.claim(1);

        assertNotNull(ended);
        assertEquals(250.0, ended.getCurrentPrice());
        assertEquals(SELLER, ended.getSellerUuid());
        assertEquals(BUYER, ended.getCurrentBidderUuid());
        assertEquals(sword, ended.getItemStack());
    }

    @Test
    @DisplayName("A second finalizer of the same bid gets nothing")
    void secondFinalizerGetsNothing() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0))));
        BidCommandManager manager = new BidCommandManager(plugin);

        assertNotNull(manager.claim(1));
        assertNull(manager.claim(1));
    }

    @Test
    @DisplayName("Two finalizers reaching the bid at the same time, only one is served")
    void concurrentFinalizersHaveASingleWinner() throws InterruptedException {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0))));
        BidCommandManager manager = new BidCommandManager(plugin);

        int threads = 32;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger delivered = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    if (manager.claim(1) != null) {
                        delivered.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "The finalizers did not finish in time");
        assertEquals(1, delivered.get());
    }

    @Test
    @DisplayName("Placing a bid raises the current price and bidder")
    void placingABidRaisesThePrice() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0))));
        BidCommandManager manager = new BidCommandManager(plugin);

        assertTrue(manager.placeBid(1, BUYER, "Buyer", 100.0, 120.0));

        Bid bid = manager.bidExist(1);
        assertEquals(120.0, bid.getCurrentPrice());
        assertEquals(BUYER, bid.getCurrentBidderUuid());
        assertEquals("Buyer", bid.getCurrentBidderName());
    }

    @Test
    @DisplayName("A bid placed against an already-outdated price is rejected")
    void staleBidIsRejected() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0))));
        when(bidQueries.updateBid(1, 100.0, 150.0, SECOND_BIDDER, "Second")).thenReturn(false);
        BidCommandManager manager = new BidCommandManager(plugin);

        // BUYER's bid is based on a read of currentPrice=100, and wins the race.
        assertTrue(manager.placeBid(1, BUYER, "Buyer", 100.0, 120.0));
        // SECOND_BIDDER read the bid before BUYER's raise went through and still thinks it is at 100.
        assertFalse(manager.placeBid(1, SECOND_BIDDER, "Second", 100.0, 150.0));

        Bid bid = manager.bidExist(1);
        assertEquals(120.0, bid.getCurrentPrice());
        assertEquals(BUYER, bid.getCurrentBidderUuid());
    }

    @Test
    @DisplayName("A finalized bid is no longer on the market")
    void finalizedBidLeavesTheMarket() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0), bid(2, SELLER, 10.0))));
        BidCommandManager manager = new BidCommandManager(plugin);

        manager.claim(1);

        assertEquals(1, manager.getBids().size());
        assertEquals(2, manager.getBids().get(0).getId());
        assertNull(manager.bidExist(1));
    }

    @Test
    @DisplayName("A failed delivery puts the bid back on the market with its current bidder")
    void failedDeliveryPutsTheBidBack() {

        ItemStack sword = namedItem(Material.DIAMOND_SWORD, 1, "Excalibur");
        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0, 250.0, BUYER, "Buyer", sword))));
        BidCommandManager manager = new BidCommandManager(plugin);

        Bid ended = manager.claim(1);
        manager.restore(ended);

        assertEquals(1, manager.getBids().size());
        Bid back = manager.getBids().get(0);
        assertEquals(250.0, back.getCurrentPrice());
        assertEquals(SELLER, back.getSellerUuid());
        assertEquals(BUYER, back.getCurrentBidderUuid());
        assertEquals(sword, back.getItemStack());
    }

    @Test
    @DisplayName("A restored bid can be finalized again, and only once")
    void restoredBidCanBeFinalizedOnce() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0))));
        BidCommandManager manager = new BidCommandManager(plugin);

        manager.restore(manager.claim(1));
        int restoredId = manager.getBids().get(0).getId();

        assertNotNull(manager.claim(restoredId));
        assertNull(manager.claim(restoredId));
    }

    @Test
    @DisplayName("Cancelling a bid that no longer exists changes nothing")
    void cancellingAnUnknownBidChangesNothing() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0))));
        BidCommandManager manager = new BidCommandManager(plugin);

        assertFalse(manager.deleteBid(404));
        assertEquals(1, manager.getBids().size());
    }

    @Test
    @DisplayName("Starting a bid puts the item on the market with its own id")
    void startingABidPutsTheItemOnTheMarket() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>());
        BidCommandManager manager = new BidCommandManager(plugin);
        Player seller = server.addPlayer();
        ItemStack sword = namedItem(Material.DIAMOND_SWORD, 1, "Excalibur");
        Date endDate = new Date(System.currentTimeMillis() + 60_000L);

        assertTrue(manager.addBid(seller, sword, 100.0, endDate));
        assertTrue(manager.addBid(seller, sword, 100.0, endDate));

        List<Bid> bids = manager.getBids();
        assertEquals(2, bids.size());
        assertEquals(sword, bids.get(0).getItemStack());
        assertEquals(100.0, bids.get(0).getStartPrice());

        // Two bids on the same item never share an id, otherwise a single claim would drop both.
        assertNotEquals(bids.get(0).getId(), bids.get(1).getId());
    }

    @Test
    @DisplayName("A new bid keeps the database's own id, so it can always be removed later")
    void newBidUsesTheDatabaseAssignedId() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(5, SELLER, 100.0))));
        when(bidQueries.addBid(any(), anyString(), any(), anyDouble(), any(), any())).thenReturn(42);
        BidCommandManager manager = new BidCommandManager(plugin);
        Player seller = server.addPlayer();

        assertTrue(manager.addBid(seller, namedItem(Material.DIAMOND, 1, "Gem"), 10.0, new Date(System.currentTimeMillis() + 60_000L)));

        assertNotNull(manager.bidExist(42));
        assertTrue(manager.deleteBid(42));
        assertNull(manager.bidExist(42));
    }

    @Test
    @DisplayName("A database write failure keeps the cache and the database in sync (SQLite)")
    void failedSqliteInsertDoesNotEnterTheCache() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>());
        when(bidQueries.addBid(any(), anyString(), any(), anyDouble(), any(), any())).thenReturn(-1);
        BidCommandManager manager = new BidCommandManager(plugin);
        Player seller = server.addPlayer();

        assertFalse(manager.addBid(seller, namedItem(Material.DIAMOND, 1, "Gem"), 10.0, new Date(System.currentTimeMillis() + 60_000L)));
        assertTrue(manager.getBids().isEmpty());
    }

    @Test
    @DisplayName("A database delete failure refuses the claim and keeps the bid on the market (SQLite)")
    void failedSqliteDeleteRefusesTheClaim() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0))));
        when(bidQueries.deleteBid(1)).thenReturn(false);
        BidCommandManager manager = new BidCommandManager(plugin);

        assertNull(manager.claim(1));
        assertNotNull(manager.bidExist(1));
        assertEquals(1, manager.getBids().size());
    }

    @Test
    @DisplayName("Saving the SQLite cache to the database re-applies the current bidder and price")
    void savingToDatabasePreservesTheCurrentBidder() {

        ItemStack sword = namedItem(Material.DIAMOND_SWORD, 1, "Excalibur");
        // Regression : a shutdown used to rewrite every bid at its start price with no bidder,
        // silently erasing the escrowed money of whoever was currently winning it.
        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0, 250.0, BUYER, "Buyer", sword))));
        BidCommandManager manager = new BidCommandManager(plugin);

        manager.saveAllBidInBddFromSQLiteCache();

        verify(bidQueries).updateBid(anyInt(), eq(100.0), eq(250.0), eq(BUYER), eq("Buyer"));
    }

    @Test
    @DisplayName("Saving a bid with no bidder to the database does not touch its price")
    void savingABidWithNoBidderDoesNotCallUpdate() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0))));
        BidCommandManager manager = new BidCommandManager(plugin);

        manager.saveAllBidInBddFromSQLiteCache();

        verify(bidQueries, never()).updateBid(anyInt(), anyDouble(), anyDouble(), any(), anyString());
    }

    @Test
    @DisplayName("Only the seller's bids are listed in his own view")
    void listsTheBidsOfASeller() {

        when(bidQueries.getBids()).thenReturn(new ArrayList<>(List.of(bid(1, SELLER, 100.0), bid(2, BUYER, 10.0))));
        BidCommandManager manager = new BidCommandManager(plugin);

        assertEquals(1, manager.getBids(SELLER).size());
        assertEquals(1, manager.getBids(SELLER).get(0).getId());
    }

    @Nested
    @DisplayName("With a shared database")
    class SharedDatabase {

        @Test
        @DisplayName("The database has the last word on who finalizes the bid")
        void databaseArbitratesTheWinner() {

            when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
            when(bidQueries.getBids()).thenReturn(new ArrayList<>());
            BidCommandManager manager = new BidCommandManager(plugin);

            when(bidQueries.getBid(1)).thenReturn(bid(1, SELLER, 100.0));
            when(bidQueries.deleteBid(1)).thenReturn(false);

            assertNull(manager.claim(1));
        }

        @Test
        @DisplayName("The winner of the delete finalizes the bid")
        void winnerOfTheDeleteGetsTheBid() {

            when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
            when(bidQueries.getBids()).thenReturn(new ArrayList<>());
            BidCommandManager manager = new BidCommandManager(plugin);

            when(bidQueries.getBid(1)).thenReturn(bid(1, SELLER, 100.0));
            when(bidQueries.deleteBid(1)).thenReturn(true);

            assertNotNull(manager.claim(1));
        }

        @Test
        @DisplayName("A bid already gone is not claimed")
        void missingBidIsNotClaimed() {

            when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
            when(bidQueries.getBids()).thenReturn(new ArrayList<>());
            BidCommandManager manager = new BidCommandManager(plugin);

            when(bidQueries.getBid(anyInt())).thenReturn(null);

            assertNull(manager.claim(1));
        }

        @Test
        @DisplayName("A failed insert is reported, so the item can be given back")
        void failedInsertIsReported() {

            when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
            when(bidQueries.getBids()).thenReturn(new ArrayList<>());
            BidCommandManager manager = new BidCommandManager(plugin);
            Player seller = server.addPlayer();

            when(bidQueries.addBid(any(), anyString(), any(), anyDouble(), any(), any())).thenReturn(-1);

            assertFalse(manager.addBid(seller, namedItem(Material.DIAMOND, 1, "Gem"), 10.0, new Date(System.currentTimeMillis() + 60_000L)));
        }
    }
}
