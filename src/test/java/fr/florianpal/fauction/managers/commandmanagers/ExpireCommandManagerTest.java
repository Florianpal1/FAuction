package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Auction;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.when;

class ExpireCommandManagerTest extends FAuctionTestBase {

    @Test
    @DisplayName("An expired auction keeps its item and its owner")
    void expiredAuctionKeepsItsItem() {

        when(expireQueries.getExpires()).thenReturn(new ArrayList<>());
        ExpireCommandManager manager = new ExpireCommandManager(plugin);
        ItemStack sword = namedItem(Material.DIAMOND_SWORD, 1, "Excalibur");

        manager.addExpire(auction(7, SELLER, 250.0, sword));

        List<Auction> expires = manager.getExpires(SELLER);
        assertEquals(1, expires.size());
        assertEquals(sword, expires.get(0).getItemStack());
        assertEquals(SELLER, expires.get(0).getPlayerUUID());
    }

    @Test
    @DisplayName("An expire gets its own id instead of reusing the auction one")
    void expireGetsItsOwnId() {

        // Regression : the expire used to be stored with the id of the auction it came from, so an
        // expire could share the id of another entry and a single withdrawal dropped them both.
        when(expireQueries.getExpires()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 10.0))));
        ExpireCommandManager manager = new ExpireCommandManager(plugin);

        manager.addExpire(auction(1, SELLER, 250.0));

        List<Auction> expires = manager.getExpires(SELLER);
        assertEquals(2, expires.size());
        assertNotEquals(expires.get(0).getId(), expires.get(1).getId());
    }

    @Test
    @DisplayName("Two auctions of the same player expiring together stay separate")
    void twoExpiresStaySeparate() {

        when(expireQueries.getExpires()).thenReturn(new ArrayList<>());
        ExpireCommandManager manager = new ExpireCommandManager(plugin);

        manager.addExpire(auction(1, SELLER, 250.0, namedItem(Material.DIAMOND_SWORD, 1, "First")));
        manager.addExpire(auction(1, SELLER, 250.0, namedItem(Material.DIAMOND_SWORD, 1, "Second")));

        int firstId = manager.getExpires(SELLER).get(0).getId();
        assertNotNull(manager.claim(firstId));

        // Withdrawing the first one leaves the second one in place.
        assertEquals(1, manager.getExpires(SELLER).size());
        assertEquals("Second", manager.getExpires(SELLER).get(0).getItemStack().getItemMeta().getDisplayName());
    }

    @Test
    @DisplayName("A burst of withdrawal packets gives the item once")
    void withdrawalBurstGivesTheItemOnce() {

        when(expireQueries.getExpires()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        ExpireCommandManager manager = new ExpireCommandManager(plugin);

        int delivered = 0;
        for (int packet = 0; packet < 200; packet++) {
            if (manager.claim(1) != null) {
                delivered++;
            }
        }

        assertEquals(1, delivered);
        assertTrue(manager.getExpires(SELLER).isEmpty());
    }

    @Test
    @DisplayName("Concurrent withdrawals of the same expire give a single winner")
    void concurrentWithdrawalsHaveASingleWinner() throws InterruptedException {

        when(expireQueries.getExpires()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        ExpireCommandManager manager = new ExpireCommandManager(plugin);

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
        assertTrue(done.await(10, TimeUnit.SECONDS), "The withdrawals did not finish in time");
        assertEquals(1, delivered.get());
    }

    @Test
    @DisplayName("Withdrawing an expire that no longer exists changes nothing")
    void withdrawingAnUnknownExpireChangesNothing() {

        when(expireQueries.getExpires()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0))));
        ExpireCommandManager manager = new ExpireCommandManager(plugin);

        assertFalse(manager.deleteExpire(404));
        assertNull(manager.claim(404));
        assertEquals(1, manager.getExpires(SELLER).size());
    }

    @Test
    @DisplayName("A player only sees his own expires")
    void playerOnlySeesHisOwnExpires() {

        when(expireQueries.getExpires()).thenReturn(new ArrayList<>(List.of(auction(1, SELLER, 250.0), auction(2, BUYER, 10.0))));
        ExpireCommandManager manager = new ExpireCommandManager(plugin);

        assertEquals(1, manager.getExpires(SELLER).size());
        assertEquals(1, manager.getExpires(BUYER).size());
    }

    @Test
    @DisplayName("With a shared database, the delete has the last word")
    void databaseArbitratesTheWithdrawal() {

        when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
        when(expireQueries.getExpires()).thenReturn(new ArrayList<>());
        ExpireCommandManager manager = new ExpireCommandManager(plugin);

        when(expireQueries.getExpire(1)).thenReturn(auction(1, SELLER, 250.0));
        when(expireQueries.deleteExpire(1)).thenReturn(false);

        assertNull(manager.claim(1));

        when(expireQueries.deleteExpire(1)).thenReturn(true);

        assertNotNull(manager.claim(1));
    }
}