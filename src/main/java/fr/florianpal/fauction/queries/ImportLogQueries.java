package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code fa_import_log} : one row per row of a source plugin already imported, so that running the
 * same import again never duplicates anything. Written in the same transaction as the data it
 * describes : after a crash, both are there or neither is.
 * <p>
 * Every method works on a connection the caller owns, and throws instead of logging.
 */
public class ImportLogQueries implements IDatabaseTable {

    public static final String TABLE = "fa_import_log";

    /**
     * The longest {@code sourceId} the table accepts. Kept under 191 characters so that the unique
     * index fits the 767 bytes of the oldest MySQL/MariaDB InnoDB formats.
     */
    public static final int MAX_SOURCE_ID_LENGTH = 191;

    /**
     * The number of values of an {@code IN (...)}, well under the limits of every driver.
     */
    private static final int IN_CHUNK = 500;

    private static final String ADD_ENTRY = "INSERT INTO " + TABLE + " (importerId, dataType, sourceId, targetTable, targetId, importedAt) VALUES(?,?,?,?,?,?)";

    /**
     * One row of the journal.
     *
     * @param targetTable the table the row landed in ({@code auctions}, {@code expires}...).
     * @param targetId    its id there, {@code null} when the driver did not hand it back.
     */
    public record Entry(String importerId, String dataType, String sourceId, String targetTable, Integer targetId,
                        long importedAt) {
    }

    private String autoIncrement = "INTEGER PRIMARY KEY AUTO_INCREMENT";

    private String parameters = "DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci";

    /**
     * The identifiers of a source are compared byte for byte : with the case-insensitive collation of
     * the other tables, {@code A1} and {@code a1} would collide on the unique index.
     */
    private String sourceIdCollation = " COLLATE utf8_bin";

    public ImportLogQueries(FAuction plugin) {
        this(plugin.getConfigurationManager().getDatabase().getSqlType());
    }

    public ImportLogQueries(SQLType sqlType) {
        if (sqlType == SQLType.SQLite) {
            autoIncrement = "INTEGER PRIMARY KEY AUTOINCREMENT";
            parameters = "";
            sourceIdCollation = "";
        } else if (sqlType == SQLType.PostgreSQL) {
            autoIncrement = "SERIAL PRIMARY KEY";
            parameters = "";
            sourceIdCollation = "";
        }
    }

    /**
     * @return among {@code sourceIds}, the ones already imported by {@code importerId} as {@code dataType}.
     */
    public Set<String> findImported(Connection connection, String importerId, String dataType, Collection<String> sourceIds) throws SQLException {
        return findImported(connection, importerId, List.of(dataType), sourceIds);
    }

    /**
     * @return among {@code sourceIds}, the ones already imported by {@code importerId} as any of
     * {@code dataTypes}.
     */
    public Set<String> findImported(Connection connection, String importerId, List<String> dataTypes, Collection<String> sourceIds) throws SQLException {
        Set<String> found = new HashSet<>();
        for (List<String> chunk : chunks(sourceIds)) {
            String sql = "SELECT sourceId FROM " + TABLE + " WHERE importerId=? AND dataType IN (" + placeholders(dataTypes.size())
                    + ") AND sourceId IN (" + placeholders(chunk.size()) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setString(index++, importerId);
                for (String dataType : dataTypes) {
                    statement.setString(index++, dataType);
                }
                for (String sourceId : chunk) {
                    statement.setString(index++, sourceId);
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        found.add(result.getString(1));
                    }
                }
            }
        }
        return found;
    }

    /**
     * Forgets that some rows were imported, for {@code --force-reimport}.
     */
    public void deleteEntries(Connection connection, String importerId, String dataType, Collection<String> sourceIds) throws SQLException {
        for (List<String> chunk : chunks(sourceIds)) {
            String sql = "DELETE FROM " + TABLE + " WHERE importerId=? AND dataType=? AND sourceId IN (" + placeholders(chunk.size()) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, importerId);
                statement.setString(2, dataType);
                for (int i = 0; i < chunk.size(); i++) {
                    statement.setString(i + 3, chunk.get(i));
                }
                statement.executeUpdate();
            }
        }
    }

    /**
     * @throws SQLException if an entry is already there (unique constraint) : the caller rolls back.
     */
    public void addEntries(Connection connection, List<Entry> entries) throws SQLException {
        if (entries.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(ADD_ENTRY)) {
            for (Entry entry : entries) {
                statement.setString(1, entry.importerId());
                statement.setString(2, entry.dataType());
                statement.setString(3, entry.sourceId());
                statement.setString(4, entry.targetTable());
                if (entry.targetId() == null) {
                    statement.setNull(5, Types.INTEGER);
                } else {
                    statement.setInt(5, entry.targetId());
                }
                statement.setLong(6, entry.importedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<List<String>> chunks(Collection<String> values) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String value : values) {
            current.add(value);
            if (current.size() == IN_CHUNK) {
                chunks.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    @Override
    public String[] getTable() {
        return new String[]{TABLE,
                "`id` " + autoIncrement + ", " +
                        "`importerId` VARCHAR(32) NOT NULL, " +
                        "`dataType` VARCHAR(20) NOT NULL, " +
                        "`sourceId` VARCHAR(" + MAX_SOURCE_ID_LENGTH + ")" + sourceIdCollation + " NOT NULL, " +
                        "`targetTable` VARCHAR(32) NOT NULL, " +
                        "`targetId` INTEGER, " +
                        "`importedAt` BIGINT NOT NULL, " +
                        "UNIQUE (`importerId`, `dataType`, `sourceId`)",
                parameters
        };
    }
}
