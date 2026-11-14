package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.CurrencyPending;
import fr.florianpal.fauction.objects.Historic;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch writes of the import, on a real database (H2) in the compatibility modes of MySQL and
 * PostgreSQL.
 */
class ImportBatchQueriesTest {

    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private TestDatabase database;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (database != null) {
            database.close();
        }
        MockBukkit.unmock();
    }

    private static byte[] item(Material material, int amount) {
        return SerializationUtil.serialize(new ItemStack(material, amount));
    }

    @ParameterizedTest
    @EnumSource(TestDatabase.Mode.class)
    @DisplayName("A batch of N rows writes N rows with their values, and hands back their ids")
    void batchesWriteEveryRow(TestDatabase.Mode mode) throws Exception {
        database = TestDatabase.create(mode);

        List<ItemRow> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(new ItemRow(SELLER, "Seller", item(Material.DIAMOND, i + 1), 10 + i, 1_000L * i));
        }

        List<Integer> auctionIds;
        List<Integer> expireIds;
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            auctionIds = database.auctionQueries.addAuctionsBatch(connection, rows);
            expireIds = database.expireQueries.addExpiresBatch(connection, rows.subList(0, 3));
            connection.commit();
        }

        assertEquals(25, database.count("auctions"));
        assertEquals(3, database.count("expires"));
        assertEquals(25, auctionIds.size());
        assertEquals(3, expireIds.size());
        assertNotNull(auctionIds.get(0), "H2 hands the generated keys back");
        assertEquals(25, Set.copyOf(auctionIds).size());

        var auctions = database.auctionQueries.getAuctions();
        assertEquals(10, auctions.get(0).getPrice());
        assertEquals(1, auctions.get(0).getItemStack().getAmount());
        assertEquals(34, auctions.get(24).getPrice());
        assertEquals(24_000L, auctions.get(24).getDate().getTime());
        assertEquals(SELLER, auctions.get(3).getPlayerUUID());
    }

    @ParameterizedTest
    @EnumSource(TestDatabase.Mode.class)
    @DisplayName("The history keeps the original sale date, or none, and the pending money its currency")
    void historicAndPendingKeepTheirValues(TestDatabase.Mode mode) throws Exception {
        database = TestDatabase.create(mode);

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            database.historicQueries.addHistoricsBatch(connection, List.of(
                    new HistoricRow(SELLER, "Seller", BUYER, "Buyer", item(Material.EMERALD, 2), 50, 1_000L, 5_000L),
                    new HistoricRow(SELLER, "Seller", BUYER, "Buyer", item(Material.EMERALD, 3), 60, 2_000L, null)));
            database.currencyPendingQueries.addBatch(connection, List.of(
                    new CurrencyPendingRow(SELLER, CurrencyType.VAULT, 12.5),
                    new CurrencyPendingRow(BUYER, CurrencyType.LEVEL, 3)));
            connection.commit();
        }

        List<Historic> historics = database.historicQueries.getHistorics(SELLER);
        assertEquals(2, historics.size());
        assertEquals(5_000L, historics.get(0).getBuyDate().getTime());
        assertEquals(BUYER, historics.get(0).getPlayerBuyerUUID());
        assertEquals(1_000L, historics.get(0).getDate().getTime());

        List<CurrencyPending> pendings = database.currencyPendingQueries.getCurrencyPending();
        assertEquals(2, pendings.size());
        assertEquals(CurrencyType.VAULT, pendings.get(0).getCurrencyType());
        assertEquals(12.5, pendings.get(0).getAmount());
        assertEquals(CurrencyType.LEVEL, pendings.get(1).getCurrencyType());
    }

    @ParameterizedTest
    @EnumSource(TestDatabase.Mode.class)
    @DisplayName("A row refused in the middle of a batch rolls the whole batch back, journal included")
    void aFailingBatchRollsBackEverything(TestDatabase.Mode mode) throws Exception {
        database = TestDatabase.create(mode);

        List<ItemRow> rows = List.of(
                new ItemRow(SELLER, "Seller", item(Material.DIAMOND, 1), 10, 1L),
                // playerName is NOT NULL : the database refuses this one.
                new ItemRow(SELLER, null, item(Material.DIAMOND, 1), 10, 1L),
                new ItemRow(SELLER, "Seller", item(Material.DIAMOND, 1), 10, 1L));

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            database.importLogQueries.addEntries(connection, List.of(
                    new ImportLogQueries.Entry("test", "auction", "a", "auctions", null, 1L)));
            assertThrows(SQLException.class, () -> database.auctionQueries.addAuctionsBatch(connection, rows));
            connection.rollback();
        }

        assertEquals(0, database.count("auctions"));
        assertEquals(0, database.count(ImportLogQueries.TABLE));
    }

    @ParameterizedTest
    @EnumSource(TestDatabase.Mode.class)
    @DisplayName("The journal refuses the same source row twice, and finds, forgets and keeps the rows")
    void theJournalIsUnique(TestDatabase.Mode mode) throws Exception {
        database = TestDatabase.create(mode);
        ImportLogQueries log = database.importLogQueries;

        try (Connection connection = database.connection()) {
            log.addEntries(connection, List.of(
                    new ImportLogQueries.Entry("test", "auction", "a", "auctions", 1, 1L),
                    new ImportLogQueries.Entry("test", "auction", "b", "auctions", 2, 1L),
                    // Same sourceId, other type or other importer : distinct rows.
                    new ImportLogQueries.Entry("test", "expired", "a", "expires", 1, 1L),
                    new ImportLogQueries.Entry("other", "auction", "a", "auctions", 3, 1L)));

            assertThrows(SQLException.class, () -> log.addEntries(connection, List.of(
                    new ImportLogQueries.Entry("test", "auction", "a", "auctions", 9, 1L))));

            assertEquals(Set.of("a", "b"), log.findImported(connection, "test", "auction", List.of("a", "b", "c")));
            assertEquals(Set.of("a"), log.findImported(connection, "test", "expired", List.of("a", "b")));

            log.deleteEntries(connection, "test", "auction", List.of("a"));
            assertEquals(Set.of("b"), log.findImported(connection, "test", "auction", List.of("a", "b")));
            assertEquals(Set.of("a"), log.findImported(connection, "other", "auction", List.of("a")));
        }
    }

    @Test
    @DisplayName("The journal is looked up in chunks : more ids than an IN list should hold")
    void manyIdsAreLookedUpInChunks() throws Exception {
        database = TestDatabase.create(TestDatabase.Mode.MYSQL);
        ImportLogQueries log = database.importLogQueries;

        List<ImportLogQueries.Entry> entries = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 1_234; i++) {
            entries.add(new ImportLogQueries.Entry("test", "auction", "id-" + i, "auctions", i, 1L));
            ids.add("id-" + i);
        }
        try (Connection connection = database.connection()) {
            log.addEntries(connection, entries);
            assertEquals(1_234, log.findImported(connection, "test", "auction", ids).size());
        }
    }

    @Test
    @DisplayName("The DDL of the journal follows the SQL type, like every other table")
    void ddlFollowsTheSqlType() {
        assertTrue(new ImportLogQueries(SQLType.MySQL).getTable()[1].contains("AUTO_INCREMENT"));
        assertTrue(new ImportLogQueries(SQLType.MariaDB).getTable()[2].contains("utf8"));
        assertTrue(new ImportLogQueries(SQLType.PostgreSQL).getTable()[1].contains("SERIAL"));
        assertFalse(new ImportLogQueries(SQLType.PostgreSQL).getTable()[2].contains("utf8"));
        for (SQLType type : SQLType.values()) {
            String[] table = new ImportLogQueries(type).getTable();
            assertEquals(ImportLogQueries.TABLE, table[0]);
            assertTrue(table[1].contains("UNIQUE (`importerId`, `dataType`, `sourceId`)"));
        }
    }
}
