package fr.florianpal.fauction.importers.auctionhouse;

import com.google.gson.JsonPrimitive;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedPendingCurrency;
import fr.florianpal.fauction.api.importer.testing.RecordingImportSink;
import fr.florianpal.fauction.testing.Fixtures;
import fr.florianpal.fauction.api.importer.testing.TestImportContext;
import fr.florianpal.fauction.testing.TestItems;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One note of the fixture per line of the conversion tables of the plan (sections 5.1 and 2.5 bis).
 */
class AuctionHouseImporterTest {

    static final UUID SELLER = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    static final UUID BIDDER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    static final Instant NOW = TestImportContext.NOW;

    @TempDir
    Path plugins;

    private final AuctionHouseImporter importer = new AuctionHouseImporter();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String note(int n) {
        return String.format("00000000-0000-0000-0000-%012d", n);
    }

    /**
     * Gson writes the dates in the US locale and the default time zone, with a narrow no-break space
     * before AM/PM since JDK 20, without the comma after the year before JDK 9.
     */
    private static String gsonDate(Instant instant, boolean commaAfterYear, char beforeAmPm) {
        String pattern = commaAfterYear ? "MMM d, yyyy, h:mm:ss" : "MMM d, yyyy h:mm:ss";
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        return format.format(Date.from(instant)) + beforeAmPm + new SimpleDateFormat("a", Locale.US).format(Date.from(instant));
    }

    static Map<String, String> tokens() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("SELLER", SELLER.toString());
        tokens.put("BUYER", BUYER.toString());
        tokens.put("BIDDER", BIDDER.toString());
        tokens.put("ITEM_DIAMOND_1", TestItems.bukkitBase64(TestItems.item(Material.DIAMOND, 1)));
        tokens.put("ITEM_DIAMOND_8", TestItems.bukkitBase64(TestItems.item(Material.DIAMOND, 8)));
        tokens.put("ITEM_DIRT_DELETED", TestItems.bukkitBase64(TestItems.named(Material.DIRT, 1, "§cDeleted item")));
        tokens.put("D_10M", gsonDate(NOW.minus(Duration.ofMinutes(10)), true, ' '));
        tokens.put("D_1H", gsonDate(NOW.minus(Duration.ofHours(1)), true, ' '));
        tokens.put("D_1D", gsonDate(NOW.minus(Duration.ofDays(1)), true, ' '));
        tokens.put("D_1D_NNBSP", gsonDate(NOW.minus(Duration.ofDays(1)), true, ' '));
        tokens.put("D_1D_OLDJDK", gsonDate(NOW.minus(Duration.ofDays(1)), false, ' '));
        tokens.put("D_3D", gsonDate(NOW.minus(Duration.ofDays(3)), true, ' '));
        return tokens;
    }

    static void install(Path plugins, String notesLocation) throws IOException {
        File folder = plugins.resolve("AuctionHouse").toFile();
        Fixtures.install("auctionhouse/notes.json", tokens(), new File(folder, notesLocation));
        Fixtures.install("auctionhouse/config.yml", Map.of(), new File(folder, "config.yml"));
        Fixtures.install("auctionhouse/layout.yml", Map.of(), new File(folder, "layout.yml"));
    }

    private RecordingImportSink read(TestImportContext context) throws Exception {
        RecordingImportSink sink = new RecordingImportSink();
        importer.read(context, sink);
        return sink;
    }

    private RecordingImportSink readFixture() throws Exception {
        install(plugins, "data/notes.json");
        return read(new TestImportContext(plugins.toFile()));
    }

    private static <T> List<T> of(List<T> rows, Predicate<T> filter) {
        return rows.stream().filter(filter).collect(Collectors.toList());
    }

    private static Optional<ImportedPendingCurrency> pending(RecordingImportSink sink, String sourceId) {
        return sink.pendingCurrencies().stream().filter(p -> p.sourceId().equals(sourceId)).findFirst();
    }

    private static ItemStack decode(fr.florianpal.fauction.api.importer.ImportedItem item) throws Exception {
        return item.itemStack().isPresent() ? item.itemStack().get() : SerializationUtil.deserializeBukkit(item.data());
    }

    @Test
    @DisplayName("Totals of the fixture : nothing lost, nothing invented")
    void totals() throws Exception {
        RecordingImportSink sink = readFixture();

        assertEquals(Set.of(note(1), note(2), note(12)), sink.auctions().stream().map(ImportedAuction::sourceId).collect(Collectors.toSet()));
        assertEquals(5, sink.expired().size(), sink.expired().toString());
        assertEquals(7, sink.pendingCurrencies().size(), sink.pendingCurrencies().toString());
        assertEquals(4, sink.historics().size(), sink.historics().toString());
        assertEquals(Set.of(note(5), note(7), note(13)), sink.skips().stream().map(RecordingImportSink.Skip::sourceId).collect(Collectors.toSet()),
                sink.skips().toString());
    }

    @Test
    @DisplayName("A sale on the market is an auction, in Vault, at its listing date")
    void activeSale() throws Exception {
        ImportedAuction auction = of(readFixture().auctions(), a -> a.sourceId().equals(note(1))).get(0);

        assertEquals(SELLER, auction.sellerUuid());
        assertEquals("§aSeller", auction.sellerName(), "the display name is passed as is, FAuction removes the colours");
        assertEquals(100.0, auction.price());
        assertEquals("Vault", auction.currency());
        assertEquals(NOW.minus(Duration.ofHours(1)), auction.listedAt());
    }

    @Test
    @DisplayName("A partially sold sale : the rest stays on sale at the same unit price, the part bought is owed to the seller")
    void partialSale() throws Exception {
        RecordingImportSink sink = readFixture();

        ImportedAuction rest = of(sink.auctions(), a -> a.sourceId().equals(note(2))).get(0);
        assertEquals(3, decode(rest.item()).getAmount());
        assertEquals(30.0, rest.price(), 1e-9);

        assertEquals(49.5, pending(sink, note(2)).orElseThrow().amount(), 1e-9, "(80 - 30) minus 1 % of tax");

        ImportedHistoric sold = of(sink.historics(), h -> h.sourceId().equals(note(2))).get(0);
        assertEquals(5, decode(sold.item()).getAmount());
        assertEquals(50.0, sold.price(), 1e-9);
        assertEquals(BUYER, sold.buyerUuid());
    }

    @Test
    @DisplayName("Expired, and expired by an administrator : given back to the seller ; deleted by an administrator : skipped")
    void expiredAndAdminActions() throws Exception {
        RecordingImportSink sink = readFixture();

        Set<String> expired = sink.expired().stream().filter(e -> e.ownerUuid().equals(SELLER)).map(ImportedExpired::sourceId).collect(Collectors.toSet());
        assertTrue(expired.containsAll(Set.of(note(3), note(4))), expired.toString());

        RecordingImportSink.Skip deleted = of(sink.skips(), s -> s.sourceId().equals(note(5))).get(0);
        assertTrue(deleted.reason().contains("Deleted by an administrator"));
        assertTrue(deleted.reason().contains("Forbidden item"));
    }

    @Test
    @DisplayName("Sold and not collected : the seller is owed the price net of the tax, and the sale goes to the history")
    void soldNotCollected() throws Exception {
        RecordingImportSink sink = readFixture();

        assertEquals(198.0, pending(sink, note(6)).orElseThrow().amount(), 1e-9);
        assertEquals("Vault", pending(sink, note(6)).orElseThrow().sourceCurrency());
        ImportedHistoric historic = of(sink.historics(), h -> h.sourceId().equals(note(6))).get(0);
        assertEquals(200.0, historic.price());
        assertNull(historic.soldAt(), "the source keeps no date of sale");

        // An older note without buyerUUID : the money is owed all the same, the history is skipped.
        assertEquals(49.5, pending(sink, note(7)).orElseThrow().amount(), 1e-9);
        assertTrue(of(sink.historics(), h -> h.sourceId().equals(note(7))).isEmpty());
        assertTrue(of(sink.skips(), s -> s.sourceId().equals(note(7))).get(0).reason().contains("buyerUUID"));
    }

    @Test
    @DisplayName("--opt apply-source-tax=false : the seller is owed the full price")
    void withoutSourceTax() throws Exception {
        install(plugins, "data/notes.json");
        RecordingImportSink sink = read(new TestImportContext(plugins.toFile()).option("apply-source-tax", "false"));

        assertEquals(200.0, pending(sink, note(6)).orElseThrow().amount(), 1e-9);
        assertEquals(50.0, pending(sink, note(2)).orElseThrow().amount(), 1e-9);
    }

    @Test
    @DisplayName("Running auction without bid : the item goes back to the seller")
    void runningBidWithoutOffer() throws Exception {
        RecordingImportSink sink = readFixture();

        ImportedExpired back = of(sink.expired(), e -> e.sourceId().equals(note(8))).get(0);
        assertEquals(SELLER, back.ownerUuid());
        assertTrue(pending(sink, note(8)).isEmpty());
    }

    @Test
    @DisplayName("Running auction with bids : the item back to the seller, every bidder refunded of their last bid")
    void runningBidWithOffers() throws Exception {
        RecordingImportSink sink = readFixture();

        assertEquals(SELLER, of(sink.expired(), e -> e.sourceId().equals(note(9))).get(0).ownerUuid());
        assertEquals(20.0, pending(sink, note(9) + ":" + BIDDER).orElseThrow().amount(), "last bid, not the sum");
        assertEquals(15.0, pending(sink, note(9) + ":" + BUYER).orElseThrow().amount());
        assertTrue(pending(sink, note(9)).isEmpty(), "the seller is owed nothing");
        assertTrue(of(sink.historics(), h -> h.sourceId().equals(note(9))).isEmpty());
    }

    @Test
    @DisplayName("Ended auction, nothing claimed : item to the winner, price to the seller, refund to the loser")
    void endedBidNothingClaimed() throws Exception {
        RecordingImportSink sink = readFixture();

        ImportedExpired won = of(sink.expired(), e -> e.sourceId().equals(note(10))).get(0);
        assertEquals(BUYER, won.ownerUuid());
        assertEquals(24.75, pending(sink, note(10)).orElseThrow().amount(), 1e-9);
        assertEquals(SELLER, pending(sink, note(10)).orElseThrow().playerUuid());
        assertEquals(10.0, pending(sink, note(10) + ":" + BIDDER).orElseThrow().amount());
        assertTrue(pending(sink, note(10) + ":" + BUYER).isEmpty(), "the winner is not refunded");

        ImportedHistoric historic = of(sink.historics(), h -> h.sourceId().equals(note(10))).get(0);
        assertEquals(BUYER, historic.buyerUuid());
        assertEquals(25.0, historic.price());
        assertEquals(NOW.minus(Duration.ofDays(1)).plusSeconds(7200 + 30), historic.soldAt());
    }

    @Test
    @DisplayName("Ended auction, everything claimed : only the history, nothing handed out twice")
    void endedBidAllClaimed() throws Exception {
        RecordingImportSink sink = readFixture();

        assertTrue(of(sink.expired(), e -> e.sourceId().equals(note(11))).isEmpty());
        assertTrue(sink.pendingCurrencies().stream().noneMatch(p -> p.sourceId().startsWith(note(11))));
        assertEquals(1, of(sink.historics(), h -> h.sourceId().equals(note(11))).size());
    }

    @Test
    @DisplayName("--bids=skip : the auctions with bids are skipped and listed")
    void bidsSkipped() throws Exception {
        install(plugins, "data/notes.json");
        RecordingImportSink sink = read(new TestImportContext(plugins.toFile()).convertBids(false));

        for (int n = 8; n <= 11; n++) {
            String id = note(n);
            assertTrue(sink.skips().stream().anyMatch(s -> s.sourceId().equals(id) && s.reason().contains("--bids=skip")), id);
            assertTrue(sink.expired().stream().noneMatch(e -> e.sourceId().startsWith(id)));
            assertTrue(sink.pendingCurrencies().stream().noneMatch(p -> p.sourceId().startsWith(id)));
        }
    }

    @Test
    @DisplayName("auctionTime 0 (old data) takes the default duration ; -1 is expired at once")
    void auctionTimeSpecialValues() throws Exception {
        RecordingImportSink sink = readFixture();

        assertEquals(1, of(sink.auctions(), a -> a.sourceId().equals(note(12))).size(), "created a day ago, two days by default");
        assertEquals(1, of(sink.expired(), e -> e.sourceId().equals(note(4))).size());
    }

    @Test
    @DisplayName("Dates : with or without U+202F, with or without the comma after the year, epoch millis")
    void dates() {
        Instant instant = NOW.minus(Duration.ofDays(1));
        assertEquals(instant, AuctionHouseImporter.parseDate(new JsonPrimitive(gsonDate(instant, true, ' '))));
        assertEquals(instant, AuctionHouseImporter.parseDate(new JsonPrimitive(gsonDate(instant, true, ' '))));
        assertEquals(instant, AuctionHouseImporter.parseDate(new JsonPrimitive(gsonDate(instant, false, ' '))));
        assertEquals(instant, AuctionHouseImporter.parseDate(new JsonPrimitive(instant.toEpochMilli())));
        assertEquals(instant.truncatedTo(ChronoUnit.SECONDS), AuctionHouseImporter.parseDate(new JsonPrimitive("2026-10-09T12:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> AuctionHouseImporter.parseDate(new JsonPrimitive("yesterday")));
    }

    @Test
    @DisplayName("A note that cannot be read is skipped with its id, the others go on")
    void unreadableNote() throws Exception {
        RecordingImportSink sink = readFixture();

        assertTrue(of(sink.skips(), s -> s.sourceId().equals(note(13))).get(0).reason().contains("Unreadable note"));
    }

    @Test
    @DisplayName("The notes.json of the older versions, at the root of the folder, is read too")
    void legacyLocation() throws Exception {
        install(plugins, "notes.json");
        TestImportContext context = new TestImportContext(plugins.toFile());

        assertTrue(importer.checkAvailability(context).available());
        assertEquals(3, read(context).auctions().size());
    }

    @Test
    @DisplayName("Empty file : nothing ; absent : unavailable ; invalid JSON : explicit error")
    void emptyAbsentInvalid() throws Exception {
        TestImportContext context = new TestImportContext(plugins.toFile());
        assertFalse(importer.checkAvailability(context).available());

        File notes = plugins.resolve("AuctionHouse/data/notes.json").toFile();
        Files.createDirectories(notes.getParentFile().toPath());
        Files.writeString(notes.toPath(), "");
        assertEquals(0, read(context).size());

        Files.writeString(notes.toPath(), "[{\"noteID\": \"x\", ");
        IOException error = assertThrows(IOException.class, () -> read(context));
        assertTrue(error.getMessage().contains("notes.json"));
    }

    @Test
    @DisplayName("The source still enabled : refused")
    void sourceEnabled() throws Exception {
        install(plugins, "data/notes.json");
        Plugin source = mock(Plugin.class);
        when(source.isEnabled()).thenReturn(true);

        assertFalse(importer.checkAvailability(new TestImportContext(plugins.toFile()).plugin("AuctionHouse", source)).available());

        when(source.isEnabled()).thenReturn(false);
        assertTrue(importer.checkAvailability(new TestImportContext(plugins.toFile()).plugin("AuctionHouse", source)).available());
    }

    @Test
    @DisplayName("The sales are evaluated when the source saved its file : a later run sees exactly the same states")
    void evaluatedAsOfTheSourceSave() throws Exception {
        RecordingImportSink atSave = readFixture();

        // Three days later, the file untouched : sale 1 (two days long) would be expired, the running
        // auction 9 ended. Nothing may change, or a second run would import them under another type.
        RecordingImportSink later = read(new TestImportContext(plugins.toFile())
                .clock(java.time.Clock.fixed(NOW.plus(Duration.ofDays(3)), java.time.ZoneId.of("UTC"))));

        assertEquals(atSave.auctions().stream().map(ImportedAuction::sourceId).toList(), later.auctions().stream().map(ImportedAuction::sourceId).toList());
        assertEquals(atSave.expired().stream().map(ImportedExpired::sourceId).toList(), later.expired().stream().map(ImportedExpired::sourceId).toList());
        assertEquals(atSave.pendingCurrencies(), later.pendingCurrencies());
    }

    @Test
    @DisplayName("A bidder already in claimedPlayers is never refunded again, even while the auction runs")
    void claimedBidderOfARunningAuction() throws Exception {
        install(plugins, "data/notes.json");
        Path notes = plugins.resolve("AuctionHouse/data/notes.json");
        String content = Files.readString(notes).replace(
                "\"bid\": 20.0}],\n   \"claimedPlayers\": []",
                "\"bid\": 20.0}],\n   \"claimedPlayers\": [\"" + BUYER + "\"]");
        assertTrue(content.contains("\"claimedPlayers\": [\"" + BUYER + "\"]"), "the fixture changed, adapt the test");
        Files.writeString(notes, content);
        notes.toFile().setLastModified(NOW.toEpochMilli());

        RecordingImportSink sink = read(new TestImportContext(plugins.toFile()));

        assertTrue(pending(sink, note(9) + ":" + BUYER).isEmpty());
        assertEquals(20.0, pending(sink, note(9) + ":" + BIDDER).orElseThrow().amount());
    }

    @Test
    @DisplayName("Two reads give the same sourceIds : a second import duplicates nothing")
    void stableSourceIds() throws Exception {
        RecordingImportSink first = readFixture();
        RecordingImportSink second = read(new TestImportContext(plugins.toFile()));

        assertEquals(first.auctions().stream().map(ImportedAuction::sourceId).toList(), second.auctions().stream().map(ImportedAuction::sourceId).toList());
        assertEquals(first.pendingCurrencies().stream().map(ImportedPendingCurrency::sourceId).toList(),
                second.pendingCurrencies().stream().map(ImportedPendingCurrency::sourceId).toList());
    }
}
