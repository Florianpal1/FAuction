package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Auction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
import static org.mockito.Mockito.when;

class AuctionCommandManagerTest extends FAuctionTestBase {

    @Test
    @DisplayName("Buying reserves the auction and gives it to the buyer")
    void buyingReservesTheAuction() {

        ItemStack sword = namedItem(Material.DIAMOND_SWORD, 1, "Excalibur");
        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0, sword))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        Auction bought = manager.claim(1);

        assertNotNull(bought);
        assertEquals(250.0, bought.getPrice());
        assertEquals(SELLER, bought.getPlayerUUID());
        assertEquals(sword, bought.getItemStack());
    }

    @Test
    @DisplayName("A second buyer on the same auction gets nothing")
    void secondBuyerGetsNothing() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        assertNotNull(manager.claim(1));
        assertNull(manager.claim(1));
    }

    @Test
    @DisplayName("A burst of purchase packets delivers the item once")
    void purchaseBurstDeliversOnce() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        int delivered = 0;
        for (int packet = 0; packet < 200; packet++) {
            if (manager.claim(1) != null) {
                delivered++;
            }
        }

        assertEquals(1, delivered);
    }

    @Test
    @DisplayName("Two buyers reaching the auction at the same time, only one is served")
    void concurrentBuyersHaveASingleWinner() throws InterruptedException {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

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
        assertTrue(done.await(10, TimeUnit.SECONDS), "The buyers did not finish in time");
        assertEquals(1, delivered.get());
    }

    @Test
    @DisplayName("A bought auction is no longer on the market")
    void boughtAuctionLeavesTheMarket() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0), auction(2, SELLER, 10.0))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        manager.claim(1);

        assertEquals(1, manager.getAuctions().size());
        assertEquals(2, manager.getAuctions().get(0).getId());
        assertNull(manager.auctionExist(1));
    }

    @Test
    @DisplayName("A failed payment puts the auction back on the market")
    void failedPaymentPutsTheAuctionBack() {

        ItemStack sword = namedItem(Material.DIAMOND_SWORD, 1, "Excalibur");
        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0, sword))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        Auction bought = manager.claim(1);
        manager.restore(bought);

        assertEquals(1, manager.getAuctions().size());
        Auction back = manager.getAuctions().get(0);
        assertEquals(250.0, back.getPrice());
        assertEquals(SELLER, back.getPlayerUUID());
        assertEquals(sword, back.getItemStack());
        assertEquals(bought.getDate(), back.getDate());
    }

    @Test
    @DisplayName("A restored auction can be bought again, and only once")
    void restoredAuctionCanBeBoughtOnce() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        manager.restore(manager.claim(1));
        int restoredId = manager.getAuctions().get(0).getId();

        assertNotNull(manager.claim(restoredId));
        assertNull(manager.claim(restoredId));
    }

    @Test
    @DisplayName("Cancelling an auction that no longer exists changes nothing")
    void cancellingAnUnknownAuctionChangesNothing() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        assertFalse(manager.deleteAuction(404));
        assertEquals(1, manager.getAuctions().size());
    }

    @Test
    @DisplayName("Selling puts the item on the market with its own id")
    void sellingPutsTheItemOnTheMarket() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>());
        AuctionCommandManager manager = new AuctionCommandManager(plugin);
        Player seller = server.addPlayer();
        ItemStack sword = namedItem(Material.DIAMOND_SWORD, 1, "Excalibur");

        assertTrue(manager.addAuction(seller, sword, 250.0));
        assertTrue(manager.addAuction(seller, sword, 250.0));

        List<Auction> auctions = manager.getAuctions();
        assertEquals(2, auctions.size());
        assertEquals(sword, auctions.get(0).getItemStack());
        assertEquals(250.0, auctions.get(0).getPrice());

        // Two sales of the same item never share an id, otherwise a single claim would drop both.
        assertNotEquals(auctions.get(0).getId(), auctions.get(1).getId());
    }

    @Test
    @DisplayName("A database write failure keeps the cache and the database in sync (SQLite)")
    void failedSqliteInsertDoesNotEnterTheCache() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>());
        when(auctionQueries.addAuction(any(), anyString(), any(), anyDouble(), any())).thenReturn(false);
        AuctionCommandManager manager = new AuctionCommandManager(plugin);
        Player seller = server.addPlayer();

        assertFalse(manager.addAuction(seller, namedItem(Material.DIAMOND, 1, "Gem"), 10.0));
        assertTrue(manager.getAuctions().isEmpty());
    }

    @Test
    @DisplayName("A database delete failure refuses the claim and keeps the auction on the market (SQLite)")
    void failedSqliteDeleteRefusesTheClaim() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        when(auctionQueries.deleteAuctions(1)).thenReturn(false);
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        assertNull(manager.claim(1));

        // The database is checked before the cache is touched, so a row that fails to delete (a
        // transient error, or a row a failed restore() never actually persisted) is never lost.
        assertNotNull(manager.auctionExist(1));
        assertEquals(1, manager.getAuctions().size());
    }

    @Test
    @DisplayName("Only the player auctions are listed in his own view")
    void listsTheAuctionsOfAPlayer() {

        when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0), auction(2, BUYER, 10.0))));
        AuctionCommandManager manager = new AuctionCommandManager(plugin);

        assertEquals(1, manager.getAuctions(SELLER).size());
        assertEquals(1, manager.getAuctions(SELLER).get(0).getId());
    }

    @Nested
    @DisplayName("With a shared database")
    class SharedDatabase {

        @Test
        @DisplayName("The database has the last word on who gets the item")
        void databaseArbitratesTheWinner() {

            // Another server deleted the row first : the local read still sees the auction, only
            // the delete tells the truth.
            when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
            when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>());
            AuctionCommandManager manager = new AuctionCommandManager(plugin);

            when(auctionQueries.getAuction(1)).thenReturn(auction(1, SELLER, 250.0));
            when(auctionQueries.deleteAuctions(1)).thenReturn(false);

            assertNull(manager.claim(1));
        }

        @Test
        @DisplayName("The winner of the delete gets the auction")
        void winnerOfTheDeleteGetsTheAuction() {

            when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
            when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>());
            AuctionCommandManager manager = new AuctionCommandManager(plugin);

            when(auctionQueries.getAuction(1)).thenReturn(auction(1, SELLER, 250.0));
            when(auctionQueries.deleteAuctions(1)).thenReturn(true);

            assertNotNull(manager.claim(1));
        }

        @Test
        @DisplayName("An auction already gone is not claimed")
        void missingAuctionIsNotClaimed() {

            when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
            when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>());
            AuctionCommandManager manager = new AuctionCommandManager(plugin);

            when(auctionQueries.getAuction(anyInt())).thenReturn(null);

            assertNull(manager.claim(1));
        }

        @Test
        @DisplayName("A failed insert is reported, so the item can be given back")
        void failedInsertIsReported() {

            when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
            when(auctionQueries.getAuctions()).thenReturn(new ArrayList<>());
            AuctionCommandManager manager = new AuctionCommandManager(plugin);
            Player seller = server.addPlayer();

            when(auctionQueries.addAuction(any(), anyString(), any(), anyDouble(), any())).thenReturn(false);

            assertFalse(manager.addAuction(seller, namedItem(Material.DIAMOND, 1, "Gem"), 10.0));
        }
    }
}
