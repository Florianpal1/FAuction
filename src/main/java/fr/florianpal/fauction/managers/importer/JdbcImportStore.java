package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.queries.AuctionQueries;
import fr.florianpal.fauction.queries.CurrencyPendingQueries;
import fr.florianpal.fauction.queries.CurrencyPendingRow;
import fr.florianpal.fauction.queries.ExpireQueries;
import fr.florianpal.fauction.queries.HistoricQueries;
import fr.florianpal.fauction.queries.HistoricRow;
import fr.florianpal.fauction.queries.ImportLogQueries;
import fr.florianpal.fauction.queries.ItemRow;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the batches of an import through the {@code *Queries} classes, sharing one transaction
 * between the data and {@code fa_import_log}.
 */
public class JdbcImportStore implements ImportStore {

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection get() throws SQLException;
    }

    private final ConnectionProvider connections;

    private final AuctionQueries auctionQueries;

    private final ExpireQueries expireQueries;

    private final HistoricQueries historicQueries;

    private final CurrencyPendingQueries currencyPendingQueries;

    private final ImportLogQueries importLogQueries;

    private final Clock clock;

    public JdbcImportStore(ConnectionProvider connections, AuctionQueries auctionQueries, ExpireQueries expireQueries,
                           HistoricQueries historicQueries, CurrencyPendingQueries currencyPendingQueries,
                           ImportLogQueries importLogQueries, Clock clock) {
        this.connections = connections;
        this.auctionQueries = auctionQueries;
        this.expireQueries = expireQueries;
        this.historicQueries = historicQueries;
        this.currencyPendingQueries = currencyPendingQueries;
        this.importLogQueries = importLogQueries;
        this.clock = clock;
    }

    @Override
    public Set<String> write(String importerId, List<PreparedRow> rows, boolean forceReimport, boolean dryRun) throws SQLException {
        try (Connection connection = connections.get()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Set<String> alreadyImported = dryRun || !forceReimport
                        ? alreadyImported(connection, importerId, rows)
                        : Set.of();

                if (dryRun) {
                    connection.rollback();
                    // A forced import would write them all the same.
                    return forceReimport ? Set.of() : alreadyImported;
                }

                if (forceReimport) {
                    for (Map.Entry<ImportDataType, List<String>> group : idsByType(rows).entrySet()) {
                        importLogQueries.deleteEntries(connection, importerId, group.getKey().id(), group.getValue());
                    }
                }

                List<PreparedRow> toWrite = new ArrayList<>();
                for (PreparedRow row : rows) {
                    if (!alreadyImported.contains(row.key())) {
                        toWrite.add(row);
                    }
                }

                insert(connection, importerId, toWrite);
                connection.commit();
                return alreadyImported;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection, e);
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(autoCommit);
                } catch (SQLException ignored) {
                    // The connection goes back to the pool, which resets it anyway.
                }
            }
        }
    }

    private Set<String> alreadyImported(Connection connection, String importerId, List<PreparedRow> rows) throws SQLException {
        Set<String> keys = new HashSet<>();
        for (Map.Entry<ImportDataType, List<String>> group : idsByType(rows).entrySet()) {
            for (String sourceId : importLogQueries.findImported(connection, importerId, sharedNamespace(group.getKey()), group.getValue())) {
                keys.add(PreparedRow.key(group.getKey(), sourceId));
            }
        }
        return keys;
    }

    /**
     * A row of the source holding an item is either on sale or waiting to be collected, and a module
     * may see it as one then as the other between two runs (the sale expired meanwhile) : the two
     * types share their identifiers, so the same item is never written once in each table.
     */
    private static List<String> sharedNamespace(ImportDataType type) {
        if (type == ImportDataType.AUCTION || type == ImportDataType.EXPIRED) {
            return List.of(ImportDataType.AUCTION.id(), ImportDataType.EXPIRED.id());
        }
        return List.of(type.id());
    }

    private void insert(Connection connection, String importerId, List<PreparedRow> rows) throws SQLException {
        Map<PreparedRow.Target, List<PreparedRow>> byTarget = new EnumMap<>(PreparedRow.Target.class);
        for (PreparedRow row : rows) {
            byTarget.computeIfAbsent(row.target(), target -> new ArrayList<>()).add(row);
        }

        long now = clock.millis();
        List<ImportLogQueries.Entry> entries = new ArrayList<>();

        for (Map.Entry<PreparedRow.Target, List<PreparedRow>> group : byTarget.entrySet()) {
            List<PreparedRow> targetRows = group.getValue();
            List<Integer> ids = switch (group.getKey()) {
                case AUCTIONS -> auctionQueries.addAuctionsBatch(connection, rows(targetRows, ItemRow.class));
                case EXPIRES -> expireQueries.addExpiresBatch(connection, rows(targetRows, ItemRow.class));
                case HISTORIC -> historicQueries.addHistoricsBatch(connection, rows(targetRows, HistoricRow.class));
                case CURRENCY_PENDING -> currencyPendingQueries.addBatch(connection, rows(targetRows, CurrencyPendingRow.class));
            };

            for (int i = 0; i < targetRows.size(); i++) {
                PreparedRow row = targetRows.get(i);
                entries.add(new ImportLogQueries.Entry(importerId, row.sourceType().id(), row.sourceId(),
                        group.getKey().table(), ids.get(i), now));
            }
        }

        importLogQueries.addEntries(connection, entries);
    }

    private static <T> List<T> rows(List<PreparedRow> rows, Class<T> type) {
        List<T> typed = new ArrayList<>(rows.size());
        for (PreparedRow row : rows) {
            typed.add(type.cast(row.row()));
        }
        return typed;
    }

    private static Map<ImportDataType, List<String>> idsByType(List<PreparedRow> rows) {
        Map<ImportDataType, List<String>> ids = new EnumMap<>(ImportDataType.class);
        for (PreparedRow row : rows) {
            ids.computeIfAbsent(row.sourceType(), type -> new ArrayList<>()).add(row.sourceId());
        }
        return ids;
    }

    private static void rollbackQuietly(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
        }
    }
}
