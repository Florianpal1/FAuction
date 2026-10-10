package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportAvailability;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportReport;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedItem;
import fr.florianpal.fauction.api.importer.ImportedPendingCurrency;
import fr.florianpal.fauction.api.importer.event.ImportFinishEvent;
import fr.florianpal.fauction.api.importer.event.ImportStartEvent;
import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.CurrencyPending;
import fr.florianpal.fauction.objects.Historic;
import fr.florianpal.fauction.queries.ImportLogQueries;
import fr.florianpal.fauction.queries.TestDatabase;
import fr.florianpal.fauction.testing.FakeImporter;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportManagerTest {

    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static final Instant LISTED = NOW.minusSeconds(600);

    @TempDir
    Path tempDir;

    private ServerMock server;

    private Plugin owner;

    private TestDatabase database;

    private ImporterRegistryImpl registry;

    private ImportSettings settings;

    private SQLType sqlType;

    private ImportStore store;

    private Executor asyncExecutor;

    private final AtomicInteger cacheRefreshes = new AtomicInteger();

    private final List<ImportReport> reports = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        owner = MockBukkit.createMockPlugin("FAuctionTest");
        registry = new ImporterRegistryImpl(Logger.getLogger("ImportManagerTest"));
        settings = settings(500, ImportSettings.Limits.NONE);
        asyncExecutor = Runnable::run;
        useDatabase(TestDatabase.Mode.MYSQL);
    }

    @AfterEach
    void tearDown() throws SQLException {
        database.close();
        MockBukkit.unmock();
    }

    private void useDatabase(TestDatabase.Mode mode) throws SQLException {
        if (database != null) {
            database.close();
        }
        database = TestDatabase.create(mode);
        sqlType = mode.sqlType();
        store = new JdbcImportStore(database::connection, database.auctionQueries, database.expireQueries,
                database.historicQueries, database.currencyPendingQueries, database.importLogQueries, clock());
    }

    private static Clock clock() {
        return Clock.fixed(NOW, ZoneId.of("UTC"));
    }

    private static ImportSettings settings(int batchSize, ImportSettings.Limits limits) {
        return new ImportSettings(batchSize, 0,
                Map.of("vault", CurrencyType.VAULT, "experience", CurrencyType.EXPERIENCE, "level", CurrencyType.LEVEL),
                CurrencyType.VAULT, 3600, limits);
    }

    private ImportManager manager() {
        return new ImportManager(new ImportManager.Environment(
                Logger.getLogger("ImportManagerTest"), sqlType, registry, store, ItemCodec.SERVER,
                uuid -> uuid.equals(SELLER) ? "ResolvedSeller" : null,
                () -> settings, clock(), asyncExecutor, Runnable::run, cacheRefreshes::incrementAndGet,
                event -> server.getPluginManager().callEvent(event), name -> null,
                tempDir.toFile(), tempDir.resolve("imports").toFile()));
    }

    private ImportReport run(ImportManager manager, FakeImporter importer, String options) {
        if (registry.find(importer.id()).isEmpty()) {
            registry.register(owner, importer);
        }
        int before = reports.size();
        assertEquals(ImportManager.StartResult.STARTED, manager.start(importer.id(), ImportOptions.parse(options), reports::add));
        assertEquals(before + 1, reports.size(), "the synchronous executor finishes the import before start returns");
        return reports.get(reports.size() - 1);
    }

    private static ImportedItem item(Material material, int amount) {
        return ImportedItem.of(new ItemStack(material, amount));
    }

    private static ImportedAuction auction(String id) {
        return new ImportedAuction(id, SELLER, "Seller", item(Material.DIAMOND, 2), 100, LISTED);
    }

    private static List<Object> auctions(int count) {
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(auction("a" + i));
        }
        return rows;
    }

    private static void assertBalanced(ImportReport report) {
        report.counts().forEach((type, counts) -> assertEquals(counts.read(),
                counts.imported() + counts.alreadyImported() + counts.rejected() + counts.failed(),
                "every row read is accounted for : " + type + " " + counts));
    }

    @Test
    @DisplayName("SQLite mode : refused, nothing read")
    void sqliteIsRefused() {
        sqlType = SQLType.SQLite;
        FakeImporter importer = new FakeImporter("fake").rows(auction("1"));
        registry.register(owner, importer);

        assertEquals(ImportManager.StartResult.REFUSED_SQLITE, manager().start("fake", ImportOptions.DEFAULTS, reports::add));
        assertEquals(0, importer.reads);
        assertTrue(reports.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(TestDatabase.Mode.class)
    @DisplayName("Every type lands in its table, with its values, and the caches are refreshed")
    void everyTypeIsImported(TestDatabase.Mode mode) throws Exception {
        useDatabase(mode);
        FakeImporter importer = new FakeImporter("fake").rows(
                new ImportedAuction("a1", SELLER, "Seller", item(Material.DIAMOND, 3), 150, LISTED, "Vault"),
                new ImportedExpired("e1", SELLER, "Seller", item(Material.EMERALD, 1), 0, LISTED),
                new ImportedHistoric("h1", SELLER, "Seller", BUYER, "Buyer", item(Material.GOLD_INGOT, 4), 40, LISTED, NOW.minusSeconds(60)),
                new ImportedPendingCurrency("p1", SELLER, "Experience", 12.5, "sale not collected"));

        ImportReport report = run(manager(), importer, "");

        assertEquals(ImportReport.Status.COMPLETED, report.status());
        assertEquals(4, report.totalImported());
        assertBalanced(report);
        assertEquals(1, cacheRefreshes.get());

        List<Auction> auctions = database.auctionQueries.getAuctions();
        assertEquals(1, auctions.size());
        assertEquals(150, auctions.get(0).getPrice());
        assertEquals(3, auctions.get(0).getItemStack().getAmount());
        assertEquals(Material.DIAMOND, auctions.get(0).getItemStack().getType());
        assertEquals(LISTED.toEpochMilli(), auctions.get(0).getDate().getTime());

        assertEquals(Material.EMERALD, database.expireQueries.getExpires().get(0).getItemStack().getType());

        Historic historic = database.historicQueries.getHistorics().get(0);
        assertEquals(BUYER, historic.getPlayerBuyerUUID());
        assertEquals(NOW.minusSeconds(60).toEpochMilli(), historic.getBuyDate().getTime());

        CurrencyPending pending = database.currencyPendingQueries.getCurrencyPending().get(0);
        assertEquals(CurrencyType.EXPERIENCE, pending.getCurrencyType());
        assertEquals(12.5, pending.getAmount());

        assertEquals(4, database.count(ImportLogQueries.TABLE));
    }

    @Test
    @DisplayName("Each validation rule refuses its row with a reason, the valid rows go through")
    void validationRules() throws Exception {
        String tooLong = "x".repeat(ImportLogQueries.MAX_SOURCE_ID_LENGTH + 1);
        FakeImporter importer = new FakeImporter("fake").rows(
                auction("ok"),
                new ImportedAuction("air", SELLER, null, item(Material.AIR, 1), 10, LISTED),
                new ImportedAuction("zero", SELLER, null, item(Material.STONE, 1), 0, LISTED),
                new ImportedAuction("negative", SELLER, null, item(Material.STONE, 1), -5, LISTED),
                new ImportedAuction("nan", SELLER, null, item(Material.STONE, 1), Double.NaN, LISTED),
                new ImportedAuction("infinite", SELLER, null, item(Material.STONE, 1), Double.POSITIVE_INFINITY, LISTED),
                new ImportedAuction(" ", SELLER, null, item(Material.STONE, 1), 1, LISTED),
                new ImportedAuction(tooLong, SELLER, null, item(Material.STONE, 1), 1, LISTED),
                new ImportedAuction("undecodable", SELLER, null, ImportedItem.ofBukkitBytes(new byte[]{1, 2, 3}), 1, LISTED),
                new ImportedExpired("negative-expired", SELLER, null, item(Material.STONE, 1), -1, LISTED),
                new ImportedHistoric("free-historic", SELLER, null, BUYER, null, item(Material.STONE, 1), 0, null, null),
                new ImportedPendingCurrency("zero-money", SELLER, "Vault", 0, null),
                new ImportedPendingCurrency("unmapped", SELLER, "CoinsEngine-coins", 10, null),
                auction("ok"));

        ImportReport report = run(manager(), importer, "");

        ImportReport.TypeCounts auctions = report.counts(ImportDataType.AUCTION);
        assertEquals(10, auctions.read());
        assertEquals(1, auctions.imported());
        assertEquals(9, auctions.rejected());
        assertEquals(1, report.counts(ImportDataType.EXPIRED).rejected());
        assertEquals(1, report.counts(ImportDataType.HISTORIC).rejected());
        assertEquals(2, report.counts(ImportDataType.PENDING_CURRENCY).rejected());
        assertBalanced(report);

        assertReason(report, "air", "air or empty");
        assertReason(report, "zero", "Invalid price");
        assertReason(report, "nan", "Invalid price");
        assertReason(report, "infinite", "Invalid price");
        assertReason(report, " ", "Empty sourceId");
        assertReason(report, tooLong, "longer than");
        assertReason(report, "undecodable", "cannot be decoded");
        assertReason(report, "unmapped", "CoinsEngine-coins is not in import.currency-map");
        assertReason(report, "zero-money", "Invalid amount");
        assertReason(report, "ok", "pushed twice");

        assertEquals(1, database.count("auctions"));
        assertEquals(0, database.count("fa_currency_pending"));
        assertEquals(1, database.count(ImportLogQueries.TABLE));
        assertTrue(Files.readString(report.logFile().toPath()).contains("CoinsEngine-coins"), "the detailed report lists the refused rows");
    }

    private static void assertReason(ImportReport report, String sourceId, String reasonPart) {
        assertTrue(report.issues().stream().anyMatch(issue -> issue.sourceId().equals(sourceId) && issue.reason().contains(reasonPart)),
                "no issue for " + sourceId + " containing \"" + reasonPart + "\" in " + report.issues());
    }

    @Test
    @DisplayName("Names : resolved from the UUID when absent, colours removed, cut to 36 characters ; dates : never in the future")
    void namesAndDates() throws Exception {
        FakeImporter importer = new FakeImporter("fake").rows(
                new ImportedAuction("resolved", SELLER, null, item(Material.STONE, 1), 1, LISTED),
                new ImportedAuction("unknown", UNKNOWN, "  ", item(Material.STONE, 1), 1, LISTED),
                new ImportedAuction("coloured", BUYER, "§x§f§f§0§0§0§0&lBu&#12ab34yer§r", item(Material.STONE, 1), 1, LISTED),
                new ImportedAuction("long", BUYER, "N".repeat(50), item(Material.STONE, 1), 1, LISTED),
                new ImportedAuction("future", BUYER, "Future", item(Material.STONE, 1), 1, NOW.plusSeconds(86_400)));

        run(manager(), importer, "");

        Map<String, String> names = new java.util.HashMap<>();
        for (Auction auction : database.auctionQueries.getAuctions()) {
            names.put(auction.getPlayerName(), auction.getPlayerName());
            if (auction.getPlayerName().equals("Future")) {
                assertEquals(NOW.toEpochMilli(), auction.getDate().getTime());
            }
        }
        assertTrue(names.containsKey("ResolvedSeller"), names.toString());
        assertTrue(names.containsKey(UNKNOWN.toString()), names.toString());
        assertTrue(names.containsKey("Buyer"), names.toString());
        assertTrue(names.containsKey("N".repeat(36)), names.toString());
    }

    @ParameterizedTest
    @EnumSource(TestDatabase.Mode.class)
    @DisplayName("Running the same import twice writes nothing the second time ; --force-reimport writes again")
    void idempotence(TestDatabase.Mode mode) throws Exception {
        useDatabase(mode);
        settings = settings(3, ImportSettings.Limits.NONE);
        FakeImporter importer = new FakeImporter("fake").rows(auctions(7));
        ImportManager manager = manager();

        ImportReport first = run(manager, importer, "");
        ImportReport second = run(manager, importer, "");

        assertEquals(7, first.counts(ImportDataType.AUCTION).imported());
        assertEquals(0, second.counts(ImportDataType.AUCTION).imported());
        assertEquals(7, second.counts(ImportDataType.AUCTION).alreadyImported());
        assertEquals(7, database.count("auctions"));

        ImportReport forced = run(manager, importer, "--force-reimport");
        assertEquals(7, forced.counts(ImportDataType.AUCTION).imported());
        assertEquals(14, database.count("auctions"));
        assertEquals(7, database.count(ImportLogQueries.TABLE), "the journal entries were replaced, not doubled");
    }

    @Test
    @DisplayName("A crash in the middle : the batches committed stay, the next run picks up exactly the rest")
    void resumeAfterACrash() throws Exception {
        settings = settings(2, ImportSettings.Limits.NONE);
        FakeImporter importer = new FakeImporter("fake").rows(auctions(9));

        ImportStore realStore = store;
        AtomicInteger writes = new AtomicInteger();
        store = (importerId, rows, force, dryRun) -> {
            if (writes.incrementAndGet() == 3) {
                // The JVM dies while the third batch is being written : it is never committed.
                throw new StackOverflowError("simulated crash");
            }
            return realStore.write(importerId, rows, force, dryRun);
        };

        ImportReport crashed = run(manager(), importer, "");
        assertEquals(ImportReport.Status.FAILED, crashed.status());
        assertEquals(4, database.count("auctions"), "two batches of two were committed");

        store = realStore;
        ImportReport resumed = run(manager(), importer, "");

        assertEquals(ImportReport.Status.COMPLETED, resumed.status());
        assertEquals(4, resumed.counts(ImportDataType.AUCTION).alreadyImported());
        assertEquals(5, resumed.counts(ImportDataType.AUCTION).imported());
        assertEquals(9, database.count("auctions"));
        assertEquals(9, database.count(ImportLogQueries.TABLE));
    }

    @Test
    @DisplayName("A batch the database refuses is retried row by row : only the bad row fails, the others carry on")
    void aFailedBatchDoesNotStopTheImport() throws Exception {
        settings = settings(3, ImportSettings.Limits.NONE);
        FakeImporter importer = new FakeImporter("fake").rows(auctions(6));

        ImportStore realStore = store;
        AtomicInteger poisoned = new AtomicInteger(1);
        store = (importerId, rows, force, dryRun) -> {
            if (poisoned.get() > 0 && rows.stream().anyMatch(row -> row.sourceId().equals("a1"))) {
                throw new SQLException("Data too long for column 'item'");
            }
            return realStore.write(importerId, rows, force, dryRun);
        };

        ImportReport report = run(manager(), importer, "");

        assertEquals(ImportReport.Status.COMPLETED, report.status());
        assertEquals(1, report.counts(ImportDataType.AUCTION).failed());
        assertEquals(5, report.counts(ImportDataType.AUCTION).imported());
        assertBalanced(report);
        assertReason(report, "a1", "Data too long");
        assertEquals(5, database.count("auctions"));

        // Not journaled : once the cause is fixed, a second run brings it.
        poisoned.set(0);
        assertEquals(1, run(manager(), importer, "").counts(ImportDataType.AUCTION).imported());
    }

    @Test
    @DisplayName("A sale imported on the market is not imported again as an expired item when it expired meanwhile")
    void aSaleExpiredBetweenTwoRunsIsNotDuplicated() throws Exception {
        ImportManager manager = manager();
        run(manager, new FakeImporter("first").rows(auction("x")), "");

        // Same module id, but the sale is now seen as expired.
        registry.unregister(registry.find("first").orElseThrow());
        FakeImporter later = new FakeImporter("first").rows(new ImportedExpired("x", SELLER, null, item(Material.DIAMOND, 2), 100, LISTED));
        ImportReport report = run(manager, later, "");

        assertEquals(1, report.counts(ImportDataType.EXPIRED).alreadyImported());
        assertEquals(1, database.count("auctions"));
        assertEquals(0, database.count("expires"));

        // Within a single run too.
        ImportReport both = run(manager(), new FakeImporter("both").rows(auction("y"),
                new ImportedExpired("y", SELLER, null, item(Material.DIAMOND, 2), 100, LISTED)), "");
        assertEquals(1, both.counts(ImportDataType.EXPIRED).rejected());
    }

    @Test
    @DisplayName("Items too big for the database, or stacks larger than the game allows, are refused before writing")
    void itemSizeLimits() throws Exception {
        ItemStack huge = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.ItemMeta meta = huge.getItemMeta();
        meta.setLore(List.of("x".repeat(40_000), "y".repeat(40_000)));
        huge.setItemMeta(meta);

        ImportReport report = run(manager(), new FakeImporter("fake").rows(
                new ImportedAuction("huge", SELLER, null, ImportedItem.of(huge), 10, LISTED),
                new ImportedAuction("stack", SELLER, null, item(Material.DIRT, 120), 10, LISTED),
                new ImportedAuction("oversized-data", SELLER, null, ImportedItem.ofBukkitBytes(new byte[2 * 1024 * 1024]), 10, LISTED),
                auction("fine")), "");

        assertReason(report, "huge", "too large for the database");
        assertReason(report, "stack", "99 max");
        assertReason(report, "oversized-data", "too large");
        assertEquals(1, database.count("auctions"));
    }

    @Test
    @DisplayName("Item data from a file cannot instantiate anything but an item")
    void untrustedDeserialization() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (org.bukkit.util.io.BukkitObjectOutputStream out = new org.bukkit.util.io.BukkitObjectOutputStream(bytes)) {
            out.writeObject(new java.text.SimpleDateFormat("yyyy"));
        }

        ImportReport report = run(manager(), new FakeImporter("fake").rows(
                new ImportedAuction("gadget", SELLER, null, ImportedItem.ofBukkitBytes(bytes.toByteArray()), 10, LISTED),
                new ImportedAuction("real", SELLER, null, ImportedItem.ofBukkitBase64(fr.florianpal.fauction.testing.TestItems.bukkitBase64(new ItemStack(Material.EMERALD, 3))), 10, LISTED)), "");

        assertReason(report, "gadget", "cannot be decoded");
        assertEquals(1, report.counts(ImportDataType.AUCTION).imported());
        assertEquals(Material.EMERALD, database.auctionQueries.getAuctions().get(0).getItemStack().getType());
    }

    @Test
    @DisplayName("On a real import thread, ImportFinishEvent is asynchronous and the report is delivered")
    void asynchronousFinishEvent() throws Exception {
        List<Boolean> asynchronous = new CopyOnWriteArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onFinish(ImportFinishEvent event) {
                asynchronous.add(event.isAsynchronous());
            }
        }, owner);
        registry.register(owner, new FakeImporter("fake").rows(auction("1")));
        asyncExecutor = task -> new Thread(task, "import-test").start();
        ImportManager manager = manager();

        assertEquals(ImportManager.StartResult.STARTED, manager.start("fake", ImportOptions.DEFAULTS, reports::add));
        assertTrue(manager.awaitCompletion(10, TimeUnit.SECONDS));
        // The latch is released once the report was handed over.
        assertEquals(List.of(true), asynchronous);
        assertEquals(1, reports.size());
    }

    @Test
    @DisplayName("callSync runs the task through the main thread executor, and refuses to be called from it")
    void callSync() throws Exception {
        ImportHelpersImpl offMainThread = new ImportHelpersImpl(tempDir.toFile(), uuid -> null, Runnable::run, ItemCodec.SERVER, () -> false);
        assertEquals(42, offMainThread.callSync(() -> 42));
        assertEquals("boom", org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> offMainThread.callSync(() -> {
                    throw new IllegalStateException("boom");
                })).getMessage());

        ImportHelpersImpl onMainThread = new ImportHelpersImpl(tempDir.toFile(), uuid -> null, Runnable::run, ItemCodec.SERVER, () -> true);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> onMainThread.callSync(() -> 1));
    }

    @Test
    @DisplayName("A module whose supportedTypes() throws supports nothing instead of breaking the command")
    void brokenSupportedTypes() {
        FakeImporter broken = new FakeImporter("broken") {
            @Override
            public Set<ImportDataType> supportedTypes() {
                throw new NullPointerException("context needed");
            }
        };
        registry.register(owner, broken);

        assertEquals(ImportManager.StartResult.NO_TYPE, manager().start("broken", ImportOptions.DEFAULTS, reports::add));
        assertTrue(ImportManager.supportedTypes(broken).isEmpty());
    }

    @Test
    @DisplayName("A dry run writes nothing and reports exactly what the import then does")
    void dryRun() throws Exception {
        settings = settings(2, ImportSettings.Limits.NONE);
        List<Object> rows = new ArrayList<>(auctions(3));
        rows.add(new ImportedAuction("bad", SELLER, null, item(Material.STONE, 1), -1, LISTED));
        FakeImporter importer = new FakeImporter("fake").rows(rows);
        ImportManager manager = manager();

        ImportReport dry = run(manager, importer, "--dry-run");
        assertTrue(dry.dryRun());
        assertEquals(0, database.count("auctions"));
        assertEquals(0, database.count(ImportLogQueries.TABLE));
        assertEquals(0, cacheRefreshes.get());

        ImportReport real = run(manager, importer, "");
        assertEquals(dry.counts(), real.counts());

        ImportReport dryAgain = run(manager, importer, "--dry-run");
        assertEquals(3, dryAgain.counts(ImportDataType.AUCTION).alreadyImported());
    }

    @Test
    @DisplayName("--types : only the requested types are read and written")
    void typesOption() throws Exception {
        FakeImporter importer = new FakeImporter("fake").rows(auction("a"),
                new ImportedExpired("e", SELLER, null, item(Material.STONE, 1), 0, LISTED));

        ImportReport report = run(manager(), importer, "--types expired");

        assertEquals(Set.of(ImportDataType.EXPIRED), report.counts().keySet());
        assertEquals(0, database.count("auctions"));
        assertEquals(1, database.count("expires"));

        FakeImporter onlyAuctions = new FakeImporter("auctions-only").types(EnumSet.of(ImportDataType.AUCTION));
        registry.register(owner, onlyAuctions);
        assertEquals(ImportManager.StartResult.NO_TYPE, manager().start("auctions-only", ImportOptions.parse("--types historic"), reports::add));
    }

    @Test
    @DisplayName("--apply-limits : a sale breaking the limits is given back to its seller instead of put on sale")
    void applyLimitsOption() throws Exception {
        settings = settings(500, new ImportSettings.Limits(Set.of(Material.BEDROCK), Map.of(Material.DIAMOND, 10.0),
                Map.of(), false, 0, false, 0));
        FakeImporter importer = new FakeImporter("fake").rows(
                new ImportedAuction("blacklisted", SELLER, null, item(Material.BEDROCK, 1), 5, LISTED),
                new ImportedAuction("too-cheap", SELLER, null, item(Material.DIAMOND, 2), 15, LISTED),
                new ImportedAuction("fine", SELLER, null, item(Material.DIAMOND, 2), 25, LISTED));

        ImportReport withoutLimits = run(manager(), importer, "--dry-run");
        assertEquals(0, withoutLimits.counts(ImportDataType.AUCTION).movedToExpires());

        ImportReport report = run(manager(), importer, "--apply-limits");
        assertEquals(3, report.counts(ImportDataType.AUCTION).imported());
        assertEquals(2, report.counts(ImportDataType.AUCTION).movedToExpires());
        assertEquals(1, database.count("auctions"));
        assertEquals(2, database.count("expires"));
    }

    @Test
    @DisplayName("--expired-to-expires : a sale older than expiration.time goes to the expired items")
    void expiredToExpiresOption() throws Exception {
        FakeImporter importer = new FakeImporter("fake").rows(
                new ImportedAuction("old", SELLER, null, item(Material.STONE, 1), 5, NOW.minusSeconds(7200)),
                new ImportedAuction("recent", SELLER, null, item(Material.STONE, 1), 5, NOW.minusSeconds(60)));

        run(manager(), importer, "--expired-to-expires");

        assertEquals(1, database.count("auctions"));
        assertEquals(1, database.count("expires"));
    }

    @Test
    @DisplayName("Without the option, an old sale stays on the market : ExpireSchedule will move it")
    void expiredSalesStayByDefault() throws Exception {
        run(manager(), new FakeImporter("fake").rows(
                new ImportedAuction("old", SELLER, null, item(Material.STONE, 1), 5, NOW.minusSeconds(7200))), "");
        assertEquals(1, database.count("auctions"));
    }

    @Test
    @DisplayName("--other-currency : a sale in another currency is refused, or given back with expire")
    void otherCurrencyOption() throws Exception {
        List<Object> rows = List.of(
                new ImportedAuction("vault", SELLER, null, item(Material.STONE, 1), 5, LISTED, "Vault"),
                new ImportedAuction("coins", SELLER, null, item(Material.STONE, 1), 5, LISTED, "CoinsEngine-coins"),
                new ImportedAuction("levels", SELLER, null, item(Material.STONE, 1), 5, LISTED, "Level"));

        ImportReport rejected = run(manager(), new FakeImporter("fake").rows(rows), "");
        assertEquals(1, rejected.counts(ImportDataType.AUCTION).imported());
        assertEquals(2, rejected.counts(ImportDataType.AUCTION).rejected());
        assertReason(rejected, "coins", "--other-currency=expire");

        ImportReport expired = run(manager(), new FakeImporter("fake"), "--other-currency=expire");
        assertEquals(1, expired.counts(ImportDataType.AUCTION).alreadyImported());
        assertEquals(2, expired.counts(ImportDataType.AUCTION).movedToExpires());
        assertEquals(1, database.count("auctions"));
        assertEquals(2, database.count("expires"));
    }

    @Test
    @DisplayName("Cancellation : the batch in progress is written, nothing after it")
    void cancellation() throws Exception {
        settings = settings(2, ImportSettings.Limits.NONE);
        CountDownLatch reached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeImporter importer = new FakeImporter("fake").rows(auctions(10)).pauseAfter(3, reached, release);
        registry.register(owner, importer);
        asyncExecutor = task -> new Thread(task, "import-test").start();
        ImportManager manager = manager();

        assertEquals(ImportManager.StartResult.STARTED, manager.start("fake", ImportOptions.DEFAULTS, reports::add));
        assertTrue(reached.await(10, TimeUnit.SECONDS));

        assertTrue(manager.current().isPresent());
        assertEquals(ImportManager.StartResult.ALREADY_RUNNING, manager.start("fake", ImportOptions.DEFAULTS, reports::add),
                "one import at a time");
        assertTrue(manager.cancel());
        release.countDown();
        assertTrue(manager.awaitCompletion(10, TimeUnit.SECONDS));

        ImportReport report = reports.get(0);
        assertEquals(ImportReport.Status.CANCELLED, report.status());
        assertEquals(3, database.count("auctions"), "two flushed, the third one of the batch in progress");
        assertTrue(manager.current().isEmpty());
        assertFalse(manager.cancel());
    }

    @Test
    @DisplayName("An exception out of read() ends the import cleanly, the rows before it are kept, the lock is released")
    void aFailingModule() throws Exception {
        FakeImporter importer = new FakeImporter("fake").rows(auctions(5)).failAfter(3);
        ImportManager manager = manager();

        ImportReport report = run(manager, importer, "");

        assertEquals(ImportReport.Status.FAILED, report.status());
        assertTrue(report.failure().contains("Source broken"));
        assertEquals(3, database.count("auctions"));
        assertTrue(manager.current().isEmpty());
        assertEquals(ImportReport.Status.FAILED, run(manager, importer, "").status(), "the lock was released");
    }

    @Test
    @DisplayName("An unavailable module reads nothing")
    void unavailableModule() {
        FakeImporter importer = new FakeImporter("fake").rows(auction("1"))
                .availability(ImportAvailability.unavailable("source still enabled"));

        ImportReport report = run(manager(), importer, "");

        assertEquals(ImportReport.Status.UNAVAILABLE, report.status());
        assertEquals("source still enabled", report.failure());
        assertEquals(0, importer.reads);
    }

    @Test
    @DisplayName("Rows skipped by the module are counted and listed")
    void skippedRows() throws Exception {
        ImportReport report = run(manager(), new FakeImporter("fake").rows(auction("1"), "deleted-by-admin"), "");

        assertEquals(1, report.skipped());
        assertReason(report, "deleted-by-admin", "skipped by the fake");
    }

    @Test
    @DisplayName("Events : ImportStartEvent cancelled reads nothing ; ImportFinishEvent carries the report")
    void events() {
        List<ImportFinishEvent> finished = new ArrayList<>();
        Listener listener = new Listener() {
            @EventHandler
            public void onStart(ImportStartEvent event) {
                if (event.getImporterId().equals("vetoed")) {
                    event.setCancelled(true);
                }
            }

            @EventHandler
            public void onFinish(ImportFinishEvent event) {
                finished.add(event);
            }
        };
        server.getPluginManager().registerEvents(listener, owner);

        FakeImporter vetoed = new FakeImporter("vetoed").rows(auction("1"));
        registry.register(owner, vetoed);
        ImportManager manager = manager();
        assertEquals(ImportManager.StartResult.CANCELLED_BY_EVENT, manager.start("vetoed", ImportOptions.DEFAULTS, reports::add));
        assertEquals(0, vetoed.reads);
        assertTrue(manager.current().isEmpty());

        ImportReport report = run(manager, new FakeImporter("fake").rows(auction("1")), "");
        assertEquals(1, finished.size());
        assertEquals(report, finished.get(0).getReport());
    }

    @Test
    @DisplayName("The detailed report is written in the imports folder")
    void reportFile() throws Exception {
        ImportReport report = run(manager(), new FakeImporter("fake").rows(auction("1")), "");

        File file = report.logFile();
        assertNotNull(file);
        assertEquals(tempDir.resolve("imports").toFile(), file.getParentFile());
        assertTrue(file.getName().startsWith("fake-20261010-"));
        String content = Files.readString(file.toPath());
        assertTrue(content.contains("auction"));
        assertTrue(content.contains("COMPLETED"));

        // Two runs in the same second do not overwrite each other.
        assertFalse(run(manager(), new FakeImporter("fake"), "").logFile().equals(file));
        assertNull(manager().current().orElse(null));
    }
}
