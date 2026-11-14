package fr.florianpal.fauction.api.importer;

import fr.florianpal.fauction.testing.TestItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImporterApiTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ImportedItem.of copies the item, in and out")
    void itemStackIsCopied() {
        ItemStack stack = new ItemStack(Material.DIAMOND, 3);
        ImportedItem item = ImportedItem.of(stack);
        stack.setAmount(1);

        assertEquals(ImportedItem.Format.ITEM_STACK, item.format());
        assertEquals(3, item.itemStack().orElseThrow().getAmount());
        assertNotSame(item.itemStack().orElseThrow(), item.itemStack().orElseThrow());
        assertEquals(0, item.data().length);
    }

    @Test
    @DisplayName("Base64 is decoded right away, line breaks of older encoders included")
    void base64IsDecodedWithTheMimeDecoder() {
        ItemStack stack = TestItems.named(Material.DIAMOND_SWORD, 1, "A long enough name to wrap the base64 over several lines");
        String plain = TestItems.bukkitBase64(stack);
        String wrapped = TestItems.bukkitBase64Mime(stack);
        assertTrue(wrapped.contains("\r\n"), "the fixture must really be wrapped");

        ImportedItem fromPlain = ImportedItem.ofBukkitBase64(plain);
        ImportedItem fromWrapped = ImportedItem.ofBukkitBase64(wrapped);

        assertEquals(ImportedItem.Format.BUKKIT_BYTES, fromPlain.format());
        assertArrayEquals(Base64.getDecoder().decode(plain), fromPlain.data());
        assertEquals(fromPlain, fromWrapped);
        assertEquals(ImportedItem.Format.PAPER_BYTES, ImportedItem.ofPaperBase64(plain).format());
    }

    @Test
    @DisplayName("Empty, null, undecodable or non GZIP data is refused with an explicit message")
    void invalidDataIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ImportedItem.ofBukkitBytes(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> ImportedItem.ofPaperBytes(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> ImportedItem.ofBukkitBase64("   "));
        assertThrows(IllegalArgumentException.class, () -> ImportedItem.ofBukkitBase64("!!!!"));
        assertThrows(IllegalArgumentException.class, () -> ImportedItem.ofBukkitBase64("abc=d"));
        assertThrows(NullPointerException.class, () -> ImportedItem.ofBukkitBase64(null));
        assertThrows(NullPointerException.class, () -> ImportedItem.of(null));

        IllegalArgumentException notGzip = assertThrows(IllegalArgumentException.class, () -> ImportedItem.ofVanillaNbtGzip(new byte[]{1, 2, 3}));
        assertTrue(notGzip.getMessage().contains("GZIP"));
    }

    @Test
    @DisplayName("The GZIP header 1F 8B is what tells the vanilla NBT apart")
    void gzipIsDetected() {
        byte[] gzip = {(byte) 0x1F, (byte) 0x8B, 8, 0};
        assertTrue(ImportedItem.isGzip(gzip));
        assertFalse(ImportedItem.isGzip(new byte[]{(byte) 0x1F}));
        assertFalse(ImportedItem.isGzip(null));

        ImportedItem item = ImportedItem.ofVanillaNbtGzip(gzip);
        assertEquals(ImportedItem.Format.VANILLA_NBT_GZIP, item.format());
        byte[] copy = item.data();
        copy[0] = 0;
        assertTrue(ImportedItem.isGzip(item.data()), "the data handed out is a copy");
    }

    @Test
    @DisplayName("Records : a missing mandatory field fails in the builder, the optional ones are accepted")
    void buildersCheckTheMandatoryFields() {
        ImportedItem item = ImportedItem.of(new ItemStack(Material.STONE));

        ImportedAuction auction = ImportedAuction.builder().sourceId("1").seller(PLAYER, null).item(item)
                .price(10).listedAt(Instant.EPOCH).build();
        assertNull(auction.sellerName());
        assertNull(auction.currency());
        assertThrows(NullPointerException.class, () -> ImportedAuction.builder().seller(PLAYER, "a").item(item).listedAt(Instant.EPOCH).build());
        assertThrows(NullPointerException.class, () -> ImportedAuction.builder().sourceId("1").item(item).listedAt(Instant.EPOCH).build());
        assertThrows(NullPointerException.class, () -> ImportedAuction.builder().sourceId("1").seller(PLAYER, "a").item(item).build());

        ImportedHistoric historic = ImportedHistoric.builder().sourceId("1").seller(PLAYER, null).buyer(PLAYER, null)
                .item(item).price(1).build();
        assertNull(historic.soldAt());
        assertNull(historic.listedAt());
        assertThrows(NullPointerException.class, () -> ImportedHistoric.builder().sourceId("1").seller(PLAYER, null).item(item).build());

        assertThrows(NullPointerException.class, () -> ImportedExpired.builder().sourceId("1").owner(PLAYER, null).listedAt(Instant.EPOCH).build());
        assertEquals("x", ImportedExpired.builder().sourceId("x").owner(PLAYER, null).item(item).listedAt(Instant.EPOCH).build().sourceId());

        ImportedPendingCurrency pending = ImportedPendingCurrency.builder().sourceId("1").player(PLAYER).currency("Vault").amount(2).build();
        assertNull(pending.reason());
        assertThrows(NullPointerException.class, () -> ImportedPendingCurrency.builder().sourceId("1").player(PLAYER).amount(2).build());
    }

    @Test
    @DisplayName("The command line names of the types")
    void typeIds() {
        assertEquals(ImportDataType.PENDING_CURRENCY, ImportDataType.byId("pending").orElseThrow());
        assertEquals(ImportDataType.PENDING_CURRENCY, ImportDataType.byId("Pending-Currency").orElseThrow());
        assertEquals(ImportDataType.EXPIRED, ImportDataType.byId(" expired ").orElseThrow());
        assertTrue(ImportDataType.byId("bid").isEmpty());
    }
}
