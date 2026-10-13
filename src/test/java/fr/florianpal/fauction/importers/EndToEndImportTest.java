package fr.florianpal.fauction.importers;

import fr.florianpal.fauction.api.importer.ImportReport;
import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.importers.auctionhouse.AuctionHouseImporter;
import fr.florianpal.fauction.importers.auctionhouse.AuctionHouseImporterTestAccess;
import fr.florianpal.fauction.importers.nexus.NexusAuctionHouseImporter;
import fr.florianpal.fauction.importers.nexus.NexusAuctionHouseImporterTestAccess;
import fr.florianpal.fauction.managers.commandmanagers.AuctionCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.ExpireCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.HistoricCommandManager;
import fr.florianpal.fauction.managers.importer.ImportManager;
import fr.florianpal.fauction.managers.importer.ImportOptions;
import fr.florianpal.fauction.managers.importer.ImportSettings;
import fr.florianpal.fauction.managers.importer.ImporterRegistryImpl;
import fr.florianpal.fauction.managers.importer.ItemCodec;
import fr.florianpal.fauction.managers.importer.JdbcImportStore;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.CurrencyPending;
import fr.florianpal.fauction.queries.ImportLogQueries;
import fr.florianpal.fauction.queries.TestDatabase;
import fr.florianpal.fauction.api.importer.testing.TestImportContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * From the files of a source plugin to what the players of FAuction see : the real modules, the real
 * import manager, a real database, read back through the managers the commands and the guis use.
 */
class EndToEndImportTest {

    private static final UUID SELLER = AuctionHouseImporterTestAccess.SELLER;

    private static final UUID BUYER = AuctionHouseImporterTestAccess.BUYER;

    @TempDir
    Path plugins;

    private ServerMock server;

    private TestDatabase database;

    private ImporterRegistryImpl registry;

    private final List<ImportReport> reports = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (database != null) {
            database.close();
        }
        MockBukkit.unmock();
    }

    private ImportManager manager(TestDatabase.Mode mode) throws SQLException {
        database = TestDatabase.create(mode);
        registry = new ImporterRegistryImpl(Logger.getLogger("EndToEndImportTest"));
        Plugin fauction = MockBukkit.createMockPlugin("FAuction");
        registry.register(fauction, new AuctionHouseImporter());
        registry.register(fauction, new NexusAuctionHouseImporter());

        Clock clock = Clock.fixed(TestImportContext.NOW, ZoneId.systemDefault());
        JdbcImportStore store = new JdbcImportStore(database::connection, database.auctionQueries, database.expireQueries,
                database.historicQueries, database.currencyPendingQueries, database.importLogQueries, clock);
        return new ImportManager(new ImportManager.Environment(
                Logger.getLogger("EndToEndImportTest"), mode.sqlType(), registry, store, ItemCodec.SERVER, uuid -> null,
                () -> new ImportSettings(4, 0, Map.of("vault", CurrencyType.VAULT), CurrencyType.VAULT, 3600, ImportSettings.Limits.NONE),
                clock, Runnable::run, Runnable::run, () -> { }, event -> server.getPluginManager().callEvent(event),
                name -> null, plugins.toFile(), plugins.resolve("FAuction/imports").toFile()));
    }

    private ImportReport run(ImportManager manager, String id) {
        assertEquals(ImportManager.StartResult.STARTED, manager.start(id, ImportOptions.DEFAULTS, reports::add));
        return reports.get(reports.size() - 1);
    }

    /**
     * Every source id written to the journal, and every one the report names : together they must
     * cover every row of the source.
     */
    private Set<String> traced(ImportReport report) throws SQLException {
        Set<String> traced = new HashSet<>();
        try (Connection connection = database.connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT sourceId FROM " + ImportLogQueries.TABLE)) {
            while (rows.next()) {
                traced.add(rows.getString(1));
            }
        }
        report.issues().forEach(issue -> traced.add(issue.sourceId()));
        return traced;
    }

    @ParameterizedTest
    @EnumSource(TestDatabase.Mode.class)
    @DisplayName("Auction-House : what the players see after the import, every note traced, the money balanced")
    void auctionHouse(TestDatabase.Mode mode) throws Exception {
        ImportManager manager = manager(mode);
        AuctionHouseImporterTestAccess.install(plugins);

        ImportReport report = run(manager, AuctionHouseImporter.ID);
        assertEquals(ImportReport.Status.COMPLETED, report.status());
        assertEquals(0, report.counts().values().stream().mapToLong(c -> c.rejected() + c.failed()).sum(), report.issues().toString());

        AuctionCommandManager auctions = new AuctionCommandManager(database.plugin);
        ExpireCommandManager expires = new ExpireCommandManager(database.plugin);
        HistoricCommandManager historics = new HistoricCommandManager(database.plugin);

        List<Auction> onSale = auctions.getAuctions(SELLER);
        assertEquals(3, onSale.size());
        assertTrue(onSale.stream().allMatch(a -> a.getPlayerName().equals("Seller")), "colours removed from the display name");
        Auction partial = onSale.stream().filter(a -> a.getItemStack().getAmount() == 3).findFirst().orElseThrow();
        assertTrue(partial.getItemStack().isSimilar(new ItemStack(Material.DIAMOND)));
        assertEquals(30.0, partial.getPrice(), 1e-9);

        assertEquals(4, expires.getExpires(SELLER).size());
        assertEquals(1, expires.getExpires(BUYER).size(), "the item won and never claimed");
        assertEquals(4, historics.getHistorics(SELLER).size());

        // Every note of the file is either imported or named in the report with its reason.
        Set<String> traced = traced(report);
        for (int n = 1; n <= 13; n++) {
            String noteId = String.format("00000000-0000-0000-0000-%012d", n);
            assertTrue(traced.stream().anyMatch(id -> id.startsWith(noteId)), "note " + n + " disappeared without a trace");
        }

        // The money owed by the source, recomputed by hand : 49.5 + 198 + 49.5 (sales net of 1 %),
        // 20 + 15 (refunds of a running auction), 24.75 + 10 (ended auction : seller and loser).
        double owed = database.currencyPendingQueries.getCurrencyPending().stream().mapToDouble(CurrencyPending::getAmount).sum();
        assertEquals(366.75, owed, 1e-9);

        // A second run brings nothing more.
        ImportReport again = run(manager, AuctionHouseImporter.ID);
        assertEquals(0, again.totalImported());
        assertEquals(report.totalImported(), again.counts().values().stream().mapToLong(ImportReport.TypeCounts::alreadyImported).sum());
    }

    @ParameterizedTest
    @EnumSource(TestDatabase.Mode.class)
    @DisplayName("NexusAuctionHouse : what the players see after the import, every sale and item traced")
    void nexus(TestDatabase.Mode mode) throws Exception {
        ImportManager manager = manager(mode);
        NexusAuctionHouseImporterTestAccess.install(plugins);

        ImportReport report = run(manager, NexusAuctionHouseImporter.ID);
        assertEquals(ImportReport.Status.COMPLETED, report.status());

        assertEquals(1, new AuctionCommandManager(database.plugin).getAuctions(SELLER).size());
        ExpireCommandManager expires = new ExpireCommandManager(database.plugin);
        assertEquals(3, expires.getExpires(SELLER).size());
        assertEquals(1, expires.getExpires(BUYER).size());
        assertEquals(2, expires.getExpires(BUYER).get(0).getItemStack().getAmount());
        assertEquals(1, new HistoricCommandManager(database.plugin).getHistorics(SELLER).size());
        assertEquals(0, database.count("fa_currency_pending"));

        // 6 sales + 4 items to collect, each one imported or named in the report.
        assertEquals(1 + 4 + 1, report.totalImported());
        assertEquals(4, report.skipped());
        assertEquals(6 + 4, report.totalImported() + report.skipped());
        assertEquals(6, database.count(ImportLogQueries.TABLE));
        assertTrue(traced(report).contains("nexus:bin:#6"));

        assertEquals(0, run(manager, NexusAuctionHouseImporter.ID).totalImported(), "a second run brings nothing more");
        assertEquals(1, new AuctionCommandManager(database.plugin).getAuctions(SELLER).size());
    }
}
