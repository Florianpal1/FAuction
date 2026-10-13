package fr.florianpal.fauction.docs.examples;

import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImporterRegistry;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.testing.RecordingImportSink;
import fr.florianpal.fauction.managers.importer.ImporterRegistryImpl;
import fr.florianpal.fauction.testing.Fixtures;
import fr.florianpal.fauction.api.importer.testing.TestImportContext;
import fr.florianpal.fauction.testing.TestItems;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The examples of the wiki, and through them the two base classes of the API : the loop, the skipped
 * rows, the requested types, the cancellation.
 */
class DocumentationExamplesTest {

    private static final UUID SELLER = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @TempDir
    Path plugins;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void installListings() throws Exception {
        Fixtures.install("example/listings.yml",
                Map.of("ITEM_DIAMOND", TestItems.bukkitBase64(TestItems.item(Material.DIAMOND, 4))),
                plugins.resolve("MyAH/listings.yml").toFile());
    }

    @Test
    @DisplayName("YAML example : every entry converted, the faulty ones skipped with a reason")
    void yamlExample() throws Exception {
        installListings();
        TestImportContext context = new TestImportContext(plugins.toFile());
        MyYamlImporter importer = new MyYamlImporter();
        RecordingImportSink sink = new RecordingImportSink();

        assertTrue(importer.checkAvailability(context).available());
        assertEquals(EnumSet.of(ImportDataType.AUCTION, ImportDataType.EXPIRED), importer.supportedTypes());
        importer.read(context, sink);

        assertEquals(1, sink.auctions().size());
        ImportedAuction auction = sink.auctions().get(0);
        assertEquals("17", auction.sourceId());
        assertEquals(SELLER, auction.sellerUuid());
        assertEquals(250.0, auction.price());
        assertEquals(Instant.ofEpochMilli(1760000000000L), auction.listedAt());

        assertEquals(1, sink.expired().size());
        assertEquals(TestImportContext.NOW, sink.expired().get(0).listedAt());

        assertEquals(Set.of("18", "19", "20"), Set.copyOf(sink.skips().stream().map(RecordingImportSink.Skip::sourceId).toList()));
        assertTrue(sink.skips().stream().anyMatch(skip -> skip.reason().equals("No seller")));
    }

    @Test
    @DisplayName("YAML example : only the requested types are read, the cancellation stops the loop")
    void yamlTypesAndCancellation() throws Exception {
        installListings();
        MyYamlImporter importer = new MyYamlImporter();

        RecordingImportSink expiredOnly = new RecordingImportSink();
        importer.read(new TestImportContext(plugins.toFile()).types(EnumSet.of(ImportDataType.EXPIRED)), expiredOnly);
        assertEquals(0, expiredOnly.auctions().size());
        assertEquals(1, expiredOnly.expired().size());

        RecordingImportSink cancelled = new RecordingImportSink();
        cancelled.cancelAfter(1);
        importer.read(new TestImportContext(plugins.toFile()), cancelled);
        assertEquals(1, cancelled.size());
    }

    @Test
    @DisplayName("YAML example : an absent file gives nothing and is reported unavailable")
    void yamlAbsentFile() throws Exception {
        TestImportContext context = new TestImportContext(plugins.toFile());
        RecordingImportSink sink = new RecordingImportSink();

        assertFalse(new MyYamlImporter().checkAvailability(context).available());
        new MyYamlImporter().read(context, sink);
        assertEquals(0, sink.size());
    }

    @Test
    @DisplayName("SQL example : read from a real database, a faulty row skipped")
    void sqlExample() throws Exception {
        String url = "jdbc:h2:mem:source-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        String item = TestItems.bukkitBase64(TestItems.item(Material.EMERALD, 2));
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sales (id BIGINT, seller VARCHAR(36), buyer VARCHAR(36), item CLOB, price DOUBLE, sold_at BIGINT)");
            statement.execute("CREATE TABLE unpaid (player VARCHAR(36), amount DOUBLE)");
            statement.execute("INSERT INTO sales VALUES (1, '" + SELLER + "', '" + UUID.randomUUID() + "', '" + item + "', 30, 1760000000)");
            statement.execute("INSERT INTO sales VALUES (2, 'broken', 'broken', '" + item + "', 30, 1760000000)");
            statement.execute("INSERT INTO unpaid VALUES ('" + SELLER + "', 12.5)");
        }

        MySqlImporter importer = new MySqlImporter();
        TestImportContext context = new TestImportContext(plugins.toFile()).option("url", url);
        RecordingImportSink sink = new RecordingImportSink();

        assertTrue(importer.checkAvailability(context).available());
        assertFalse(importer.checkAvailability(new TestImportContext(plugins.toFile())).available());
        importer.read(context, sink);

        assertEquals(1, sink.historics().size());
        assertEquals("sale-1", sink.historics().get(0).sourceId());
        assertEquals(Instant.ofEpochSecond(1760000000), sink.historics().get(0).soldAt());
        assertEquals(1, sink.pendingCurrencies().size());
        assertEquals(1, sink.skips().size());
        assertEquals("historic#2", sink.skips().get(0).sourceId());

        RecordingImportSink pendingOnly = new RecordingImportSink();
        importer.read(new TestImportContext(plugins.toFile()).option("url", url).types(EnumSet.of(ImportDataType.PENDING_CURRENCY)), pendingOnly);
        assertEquals(1, pendingOnly.size());
    }

    @Test
    @DisplayName("Registration example : the module lands in the registry of the ServicesManager")
    void registrationExample() {
        Plugin fauction = MockBukkit.createMockPlugin("FAuction");
        Plugin module = MockBukkit.createMockPlugin("MyAHImporter");
        ImporterRegistryImpl registry = new ImporterRegistryImpl(Logger.getLogger("DocumentationExamplesTest"));
        server.getServicesManager().register(ImporterRegistry.class, registry, fauction, ServicePriority.Normal);

        RegistrationExample.onEnable(module);

        assertTrue(registry.find("myah").isPresent());
    }
}
