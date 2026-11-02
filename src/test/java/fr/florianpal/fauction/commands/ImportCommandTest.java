package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.api.importer.ImportAvailability;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedItem;
import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.languages.TestLang;
import fr.florianpal.fauction.managers.importer.ImportManager;
import fr.florianpal.fauction.managers.importer.ImportSettings;
import fr.florianpal.fauction.managers.importer.ImporterRegistryImpl;
import fr.florianpal.fauction.managers.importer.ItemCodec;
import fr.florianpal.fauction.managers.importer.JdbcImportStore;
import fr.florianpal.fauction.queries.TestDatabase;
import fr.florianpal.fauction.testing.FakeImporter;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The handlers of {@code /ah admin import}, run from the console as an administrator would during a
 * maintenance, against a real database.
 */
class ImportCommandTest extends FAuctionTestBase {

    @TempDir
    Path tempDir;

    private TestDatabase database;

    private ImporterRegistryImpl registry;

    private AuctionCommand command;

    private ConsoleCommandSenderMock console;

    private FakeImporter importer;

    @BeforeEach
    void setUpImport() throws SQLException {
        database = TestDatabase.create(TestDatabase.Mode.MYSQL);
        registry = new ImporterRegistryImpl(Logger.getLogger("ImportCommandTest"));
        Plugin owner = MockBukkit.createMockPlugin("Owner");
        importer = new FakeImporter("fake").rows(
                new ImportedAuction("1", SELLER, "Seller", ImportedItem.of(new ItemStack(Material.DIAMOND)), 10, Instant.now()));
        registry.register(owner, importer);

        when(globalConfig.getDecimalFormat()).thenReturn("0.00");
        when(plugin.getLang()).thenReturn(TestLang.of("en"));
        when(databaseConfig.getSqlType()).thenReturn(SQLType.MySQL);
        useManager(SQLType.MySQL);

        command = new AuctionCommand(plugin);
        console = server.getConsoleSender();
    }

    @AfterEach
    void tearDownImport() throws SQLException {
        database.close();
    }

    private void useManager(SQLType sqlType) {
        JdbcImportStore store = new JdbcImportStore(database::connection, database.auctionQueries, database.expireQueries,
                database.historicQueries, database.currencyPendingQueries, database.importLogQueries, Clock.systemUTC());
        ImportManager manager = new ImportManager(new ImportManager.Environment(
                Logger.getLogger("ImportCommandTest"), sqlType, registry, store, ItemCodec.SERVER, uuid -> null,
                () -> new ImportSettings(500, 0, Map.of("vault", CurrencyType.VAULT), CurrencyType.VAULT, 3600, ImportSettings.Limits.NONE),
                Clock.systemUTC(), Runnable::run, Runnable::run, () -> { }, event -> server.getPluginManager().callEvent(event),
                name -> null, tempDir.toFile(), tempDir.resolve("imports").toFile()));
        when(plugin.getImportManager()).thenReturn(manager);
    }

    private static List<String> messages(CommandSender sender) {
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = next(sender)) != null) {
            messages.add(message);
        }
        return messages;
    }

    private static String next(CommandSender sender) {
        if (sender instanceof ConsoleCommandSenderMock console) {
            return console.nextMessage();
        }
        return ((PlayerMock) sender).nextMessage();
    }

    private static boolean contains(List<String> messages, String part) {
        return messages.stream().anyMatch(message -> message.contains(part));
    }

    @Test
    @DisplayName("A dry run starts at once from the console and reports, writing nothing")
    void dryRunFromTheConsole() throws Exception {
        command.onImportRun(console, importer, "--dry-run");

        List<String> messages = messages(console);
        assertTrue(contains(messages, "Import fake started (dry run)"), messages.toString());
        assertTrue(contains(messages, "1 imported"), messages.toString());
        assertTrue(contains(messages, "Detailed report"), messages.toString());
        assertEquals(0, database.count("auctions"));
    }

    @Test
    @DisplayName("A real import has to be confirmed, and runs with the options of the first typing")
    void realImportNeedsConfirmation() throws Exception {
        command.onImportRun(console, importer, "--types auction");
        List<String> first = messages(console);
        assertTrue(contains(first, "/ah admin import run fake confirm"), first.toString());
        assertEquals(0, importer.reads);

        command.onImportRun(console, importer, "confirm");
        List<String> second = messages(console);
        assertTrue(contains(second, "Import fake started (import)"), second.toString());
        assertEquals(1, database.count("auctions"));

        // Used once.
        command.onImportRun(console, importer, "confirm");
        assertTrue(contains(messages(console), "Nothing to confirm"));
        assertEquals(1, importer.reads);
    }

    @Test
    @DisplayName("Another sender cannot confirm the import asked by the console")
    void confirmationIsPerSender() throws Exception {
        PlayerMock admin = server.addPlayer();

        command.onImportRun(console, importer, "");
        command.onImportRun(admin, importer, "confirm");

        assertTrue(contains(messages(admin), "Nothing to confirm"));
        assertEquals(0, importer.reads);
    }

    @Test
    @DisplayName("An unavailable module is refused before the confirmation is asked")
    void unavailableBeforeConfirmation() {
        importer.availability(ImportAvailability.unavailable("AuctionHouse is still enabled"));

        command.onImportRun(console, importer, "");

        List<String> messages = messages(console);
        assertTrue(contains(messages, "AuctionHouse is still enabled"), messages.toString());
        command.onImportRun(console, importer, "confirm");
        assertTrue(contains(messages(console), "Nothing to confirm"));
    }

    @Test
    @DisplayName("The availability is checked with the options typed : a module needing --opt can run for real")
    void availabilityUsesTheOptions() throws Exception {
        FakeImporter needsUrl = new FakeImporter("needs-url") {
            @Override
            public ImportAvailability checkAvailability(fr.florianpal.fauction.api.importer.ImportContext context) {
                return context.options().containsKey("url")
                        ? ImportAvailability.available("ok")
                        : ImportAvailability.unavailable("give --opt url=...");
            }
        }.rows(new ImportedAuction("1", SELLER, "Seller", ImportedItem.of(new ItemStack(Material.DIAMOND)), 10, Instant.now()));
        registry.register(MockBukkit.createMockPlugin("Other"), needsUrl);

        command.onImportRun(console, needsUrl, "");
        assertTrue(contains(messages(console), "give --opt url"));

        command.onImportRun(console, needsUrl, "--opt url=jdbc:h2:mem:x");
        assertTrue(contains(messages(console), "confirm"));
        command.onImportRun(console, needsUrl, "confirm");
        assertTrue(contains(messages(console), "Import needs-url started"));
        assertEquals(1, database.count("auctions"));
    }

    @Test
    @DisplayName("--force-reimport is confirmed with an explicit warning")
    void forceReimportWarning() {
        command.onImportRun(console, importer, "--force-reimport");

        List<String> messages = messages(console);
        assertTrue(contains(messages, "--force-reimport ignores the journal"), messages.toString());
        assertTrue(contains(messages, "confirm"), messages.toString());
    }

    @Test
    @DisplayName("SQLite : every run is refused with the reason")
    void sqliteIsRefused() {
        useManager(SQLType.SQLite);

        command.onImportRun(console, importer, "--dry-run");

        assertTrue(contains(messages(console), "shared SQL database"));
        assertEquals(0, importer.reads);
    }

    @Test
    @DisplayName("Invalid options are reported, nothing runs")
    void invalidOptions() {
        command.onImportRun(console, importer, "--types bids");

        assertTrue(contains(messages(console), "Invalid options"));
        assertEquals(0, importer.reads);
    }

    @Test
    @DisplayName("list shows each module and whether it can run ; status and cancel with nothing running")
    void listStatusCancel() {
        registry.register(MockBukkit.createMockPlugin("Other"),
                new FakeImporter("broken").availability(ImportAvailability.unavailable("files missing")));

        command.onImportList(console);
        List<String> list = messages(console);
        assertTrue(contains(list, "fake"), list.toString());
        assertTrue(contains(list, "files missing"), list.toString());

        command.onImportStatus(console);
        assertTrue(contains(messages(console), "No import is running"));

        command.onImportCancel(console);
        assertTrue(contains(messages(console), "No import is running"));
    }

    @Test
    @DisplayName("The importer parser finds the registered modules and refuses the others")
    void importerParser() {
        assertEquals(List.of("fake"), command.importerSuggestions());
        assertEquals(importer, command.parseImporter(org.incendo.cloud.context.CommandInput.of("fake")));
        AuctionCommand.UnknownImporterException error = org.junit.jupiter.api.Assertions.assertThrows(
                AuctionCommand.UnknownImporterException.class,
                () -> command.parseImporter(org.incendo.cloud.context.CommandInput.of("nope")));
        assertEquals("nope", error.getInput());
    }
}
