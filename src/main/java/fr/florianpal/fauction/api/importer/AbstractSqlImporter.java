package fr.florianpal.fauction.api.importer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Base class of a module reading a SQL database. The module gives the connection, one query per data
 * type and a {@link RowMapper} ; the loop, the closing of the resources, the cancellation and the
 * errors of a single row (skipped instead of stopping the import) are handled here.
 * <pre>{@code
 * public class MyImporter extends AbstractSqlImporter {
 *     protected Connection openConnection(ImportContext ctx) throws Exception {
 *         return ctx.helpers().openJdbc("jdbc:sqlite:" + new File(ctx.helpers().sourceDataFolder("MyAH"), "data.db"), "", "");
 *     }
 *     protected List<SqlSource<?>> sources(ImportContext ctx) {
 *         return List.of(new SqlSource<>(ImportDataType.AUCTION, "SELECT * FROM listings", (row, c) ->
 *             new ImportedAuction(row.getString("id"), UUID.fromString(row.getString("seller")), null,
 *                 ImportedItem.ofBukkitBase64(row.getString("item")), row.getDouble("price"),
 *                 Instant.ofEpochMilli(row.getLong("created")))));
 *     }
 *     // id(), displayName(), supportedTypes(), checkAvailability()...
 * }
 * }</pre>
 */
public abstract class AbstractSqlImporter implements DataImporter {

    /**
     * A query and the way its rows are converted.
     *
     * @param type   the data type the rows are imported as.
     * @param sql    the query, run once, without parameters.
     * @param mapper converts one row.
     */
    public record SqlSource<T extends ImportedRow>(ImportDataType type, String sql, RowMapper<T> mapper) {

        public SqlSource {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(sql, "sql");
            Objects.requireNonNull(mapper, "mapper");
        }
    }

    /**
     * Opens the connection to the source database ; closed by this class.
     */
    protected abstract Connection openConnection(ImportContext context) throws Exception;

    /**
     * The queries to run, in that order. Only the ones of a requested type are run.
     */
    protected abstract List<SqlSource<?>> sources(ImportContext context);

    @Override
    public void read(ImportContext context, ImportSink sink) throws Exception {
        try (Connection connection = openConnection(context)) {
            for (SqlSource<?> source : sources(context)) {
                if (sink.isCancelled()) {
                    return;
                }
                if (!context.requestedTypes().contains(source.type())) {
                    continue;
                }
                readSource(connection, source, context, sink);
            }
        }
    }

    private void readSource(Connection connection, SqlSource<?> source, ImportContext context, ImportSink sink) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(source.sql());
             ResultSet rows = statement.executeQuery()) {
            long rowNumber = 0;
            while (!sink.isCancelled() && rows.next()) {
                rowNumber++;
                try {
                    ImportSinks.emit(sink, source.type(), source.mapper().map(rows, context));
                } catch (ImportSkipException skip) {
                    sink.skip(skip.getSourceId(), skip.getMessage());
                } catch (SQLException e) {
                    // The connection or the query broke, not this row : every next row would fail the
                    // same way, the import stops instead of skipping them all.
                    throw e;
                } catch (Exception e) {
                    sink.skip(source.type().id() + "#" + rowNumber, ImportSinks.describe(e));
                }
            }
        }
    }
}
