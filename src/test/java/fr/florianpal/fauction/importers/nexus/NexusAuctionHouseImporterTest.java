package fr.florianpal.fauction.importers.nexus;

import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.testing.RecordingImportSink;
import fr.florianpal.fauction.testing.Fixtures;
import fr.florianpal.fauction.api.importer.testing.TestImportContext;
import fr.florianpal.fauction.testing.TestItems;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One sale of the fixture per line of the conversion table of the plan (section 5.2).
 */
class NexusAuctionHouseImporterTest {

    static final UUID SELLER = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    static final Instant NOW = TestImportContext.NOW;

    @TempDir
    Path plugins;

    private final NexusAuctionHouseImporter importer = new NexusAuctionHouseImporter();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    static Map<String, String> tokens(boolean expiryEnabled, int idOffset) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("SELLER", SELLER.toString());
        tokens.put("BUYER", BUYER.toString());
        tokens.put("ITEM_DIAMOND_1", TestItems.bukkitBase64(TestItems.item(Material.DIAMOND, 1)));
        tokens.put("ITEM_DIAMOND_2", TestItems.bukkitBase64(TestItems.item(Material.DIAMOND, 2)));
        tokens.put("ITEM_EMERALD_1", TestItems.bukkitBase64(TestItems.item(Material.EMERALD, 1)));
        tokens.put("ITEM_GOLD_1", TestItems.bukkitBase64(TestItems.item(Material.GOLD_INGOT, 1)));
        tokens.put("ITEM_IRON_1", TestItems.bukkitBase64(TestItems.item(Material.IRON_INGOT, 1)));
        tokens.put("E_PLUS_1D", String.valueOf(NOW.plus(Duration.ofDays(1)).getEpochSecond()));
        tokens.put("E_MINUS_1D", String.valueOf(NOW.minus(Duration.ofDays(1)).getEpochSecond()));
        tokens.put("E_MINUS_2D", String.valueOf(NOW.minus(Duration.ofDays(2)).getEpochSecond()));
        tokens.put("EXPIRY_ENABLE", String.valueOf(expiryEnabled));
        for (int i = 1; i <= 6; i++) {
            tokens.put("ID_" + i, String.valueOf(i + idOffset));
        }
        return tokens;
    }

    static void install(Path plugins, boolean expiryEnabled, int idOffset) throws IOException {
        File folder = plugins.resolve("NexusAuctionHouse").toFile();
        Map<String, String> tokens = tokens(expiryEnabled, idOffset);
        Fixtures.install("nexus/bins.json", tokens, new File(folder, "data/bins.json"));
        Fixtures.install("nexus/expired-retrieve.json", tokens, new File(folder, "data/expired-retrieve.json"));
        Fixtures.install("nexus/config.yml", tokens, new File(folder, "config.yml"));
    }

    private RecordingImportSink read(TestImportContext context) throws Exception {
        RecordingImportSink sink = new RecordingImportSink();
        importer.read(context, sink);
        return sink;
    }

    private RecordingImportSink readFixture() throws Exception {
        install(plugins, true, 0);
        return read(new TestImportContext(plugins.toFile()));
    }

    private static Material type(fr.florianpal.fauction.api.importer.ImportedItem item) throws Exception {
        return SerializationUtil.deserializeBukkit(item.data()).getType();
    }

    private static List<String> skipReasons(RecordingImportSink sink) {
        return sink.skips().stream().map(RecordingImportSink.Skip::reason).collect(Collectors.toList());
    }

    @Test
    @DisplayName("A sale on the market is an auction, its listing date deduced from expiry.time")
    void activeSale() throws Exception {
        RecordingImportSink sink = readFixture();

        assertEquals(1, sink.auctions().size(), sink.auctions().toString());
        ImportedAuction auction = sink.auctions().get(0);
        assertEquals(SELLER, auction.sellerUuid());
        assertNull(auction.sellerName(), "the source keeps no names, FAuction resolves them");
        assertEquals(100.0, auction.price());
        assertEquals(NOW.plus(Duration.ofDays(1)).minus(Duration.ofDays(7)), auction.listedAt());
        assertEquals(Material.DIAMOND, type(auction.item()));
    }

    @Test
    @DisplayName("Every item of expired-retrieve.json is an expired item of its owner, at price 0")
    void collectableItems() throws Exception {
        RecordingImportSink sink = readFixture();

        assertEquals(4, sink.expired().size(), sink.expired().toString());
        assertEquals(3, sink.expired().stream().filter(e -> e.ownerUuid().equals(SELLER)).count());
        assertEquals(1, sink.expired().stream().filter(e -> e.ownerUuid().equals(BUYER)).count());
        assertTrue(sink.expired().stream().allMatch(e -> e.price() == 0 && e.listedAt().equals(NOW)));
        assertEquals(4, sink.expired().stream().map(ImportedExpired::sourceId).distinct().count(),
                "two identical items of the same player are two rows");
    }

    @Test
    @DisplayName("An expired sale is not imported from bins.json : its item is already in the player's list")
    void expiredSaleIsNotDuplicated() throws Exception {
        RecordingImportSink sink = readFixture();

        long emeralds = 0;
        for (ImportedExpired expired : sink.expired()) {
            if (type(expired.item()) == Material.EMERALD) {
                emeralds++;
            }
        }
        assertEquals(2, emeralds, "the two of the list, not a third one from the expired sale");
        assertTrue(skipReasons(sink).stream().anyMatch(reason -> reason.contains("imported from expired-retrieve.json")));
    }

    @Test
    @DisplayName("recover-orphans : an expired sale whose item is in no list is given back, only with the option")
    void orphans() throws Exception {
        RecordingImportSink withoutOption = readFixture();
        assertTrue(withoutOption.expired().stream().noneMatch(e -> e.sourceId().startsWith("nexus:bin:")));
        assertTrue(skipReasons(withoutOption).stream().anyMatch(reason -> reason.contains("recover-orphans=true")));

        RecordingImportSink withOption = read(new TestImportContext(plugins.toFile()).option("recover-orphans", "true"));
        List<ImportedExpired> recovered = withOption.expired().stream().filter(e -> e.sourceId().startsWith("nexus:bin:")).toList();
        assertEquals(1, recovered.size(), "the gold ingot only : the emerald is in the list");
        assertEquals(Material.GOLD_INGOT, type(recovered.get(0).item()));
        assertEquals(SELLER, recovered.get(0).ownerUuid());
        assertEquals(30.0, recovered.get(0).price());
    }

    @Test
    @DisplayName("A bought sale is history only ; a withdrawn one is skipped ; money is never pending")
    void boughtAndWithdrawn() throws Exception {
        RecordingImportSink sink = readFixture();

        assertEquals(1, sink.historics().size());
        ImportedHistoric historic = sink.historics().get(0);
        assertEquals(SELLER, historic.sellerUuid());
        assertEquals(BUYER, historic.buyerUuid());
        assertEquals(40.0, historic.price());
        assertNull(historic.soldAt());

        assertTrue(skipReasons(sink).stream().anyMatch(reason -> reason.contains("Withdrawn by its seller")));
        assertEquals(0, sink.pendingCurrencies().size());
    }

    @Test
    @DisplayName("An unreadable sale is skipped, the others go on ; the global money counter is ignored")
    void unreadableSale() throws Exception {
        RecordingImportSink sink = readFixture();

        assertTrue(sink.skips().stream().anyMatch(skip -> skip.sourceId().equals("nexus:bin:#6")));
        // 1 auction + 1 history + 4 collectable, and 4 skips : expired, orphan, withdrawn, unreadable.
        assertEquals(6, sink.size());
        assertEquals(4, sink.skips().size(), sink.skips().toString());
    }

    @Test
    @DisplayName("expiry.enable false : every sale without buyer is on the market, listed at its \"expiry\"")
    void expiryDisabled() throws Exception {
        install(plugins, false, 0);
        RecordingImportSink sink = read(new TestImportContext(plugins.toFile()));

        assertEquals(3, sink.auctions().size(), "the active one and the two that would be expired");
        assertTrue(sink.auctions().stream().anyMatch(a -> a.listedAt().equals(NOW.minus(Duration.ofDays(2)))));
    }

    @Test
    @DisplayName("The sourceIds are stable across two reads and when the source renumbers its ids")
    void stableSourceIds() throws Exception {
        RecordingImportSink first = readFixture();
        Set<String> firstIds = ids(first);

        install(plugins, true, 1000);
        Set<String> renumbered = ids(read(new TestImportContext(plugins.toFile())));

        assertEquals(firstIds, renumbered);
        assertEquals(firstIds, ids(read(new TestImportContext(plugins.toFile()))));
    }

    private static Set<String> ids(RecordingImportSink sink) {
        Set<String> ids = new java.util.HashSet<>();
        sink.auctions().forEach(a -> ids.add("a" + a.sourceId()));
        sink.expired().forEach(e -> ids.add("e" + e.sourceId()));
        sink.historics().forEach(h -> ids.add("h" + h.sourceId()));
        sink.skips().forEach(s -> ids.add("s" + s.sourceId()));
        return ids;
    }

    @Test
    @DisplayName("Empty files give nothing, a truncated file an explicit error, absent files unavailable")
    void emptyTruncatedAbsent() throws Exception {
        TestImportContext context = new TestImportContext(plugins.toFile());
        assertFalse(importer.checkAvailability(context).available());

        File data = plugins.resolve("NexusAuctionHouse/data").toFile();
        Files.createDirectories(data.toPath());
        Files.writeString(new File(data, "bins.json").toPath(), "");
        Files.writeString(new File(data, "expired-retrieve.json").toPath(), "");
        assertTrue(importer.checkAvailability(context).available());
        assertEquals(0, read(context).size());

        Files.writeString(new File(data, "bins.json").toPath(), "{\"money\": 1, \"bins\": [{\"id\": 1, \"sel");
        IOException error = assertThrows(IOException.class, () -> read(context));
        assertTrue(error.getMessage().contains("bins.json"));
    }

    @Test
    @DisplayName("The source still enabled : refused")
    void sourceEnabled() throws Exception {
        install(plugins, true, 0);
        Plugin source = mock(Plugin.class);
        when(source.isEnabled()).thenReturn(true);

        assertFalse(importer.checkAvailability(new TestImportContext(plugins.toFile()).plugin("NexusAuctionHouse", source)).available());
    }

    @Test
    @DisplayName("The duration syntax of the source")
    void durations() {
        assertEquals(7 * 86_400, NexusAuctionHouseImporter.parseDuration("7d"));
        assertEquals(90, NexusAuctionHouseImporter.parseDuration("90"));
        assertEquals(86_400 + 2 * 3_600 + 30 * 60, NexusAuctionHouseImporter.parseDuration("1d2h30m"));
        assertEquals(30L * 86_400, NexusAuctionHouseImporter.parseDuration("1mo"));
        assertEquals(0, NexusAuctionHouseImporter.parseDuration(""));
    }
}
