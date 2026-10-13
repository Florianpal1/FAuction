package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.DatabaseConfig;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.ConfigurationManager;
import fr.florianpal.fauction.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A real database for the tests : H2 in memory, in the compatibility mode of a shared SQL database,
 * with the tables of FAuction created from the very DDL the plugin runs.
 * <p>
 * The plugin is a mock whose {@link DatabaseManager} hands out connections to that database, so the
 * real {@code *Queries} classes run unchanged against it.
 */
public final class TestDatabase implements AutoCloseable {

    /**
     * The shared SQL databases the import supports, as H2 compatibility modes.
     */
    public enum Mode {
        MYSQL("MySQL", SQLType.MySQL),
        POSTGRESQL("PostgreSQL", SQLType.PostgreSQL);

        private final String h2Mode;

        private final SQLType sqlType;

        Mode(String h2Mode, SQLType sqlType) {
            this.h2Mode = h2Mode;
            this.sqlType = sqlType;
        }

        public SQLType sqlType() {
            return sqlType;
        }
    }

    private final String url;

    private final Connection keepAlive;

    private final Mode mode;

    public final FAuction plugin;

    public final DatabaseManager databaseManager;

    public final AuctionQueries auctionQueries;

    public final ExpireQueries expireQueries;

    public final HistoricQueries historicQueries;

    public final CurrencyPendingQueries currencyPendingQueries;

    public final ImportLogQueries importLogQueries;

    private TestDatabase(Mode mode) throws SQLException {
        this.mode = mode;
        this.url = "jdbc:h2:mem:fauction-" + UUID.randomUUID() + ";MODE=" + mode.h2Mode + ";DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=DATE,VALUE";
        // An in-memory H2 database lives as long as one connection is open on it.
        this.keepAlive = DriverManager.getConnection(url);

        plugin = mock(FAuction.class);
        databaseManager = mock(DatabaseManager.class);
        DatabaseConfig databaseConfig = mock(DatabaseConfig.class);
        GlobalConfig globalConfig = mock(GlobalConfig.class);
        ConfigurationManager configurationManager = mock(ConfigurationManager.class);

        when(databaseConfig.getSqlType()).thenReturn(mode.sqlType);
        when(globalConfig.getOrderBy()).thenReturn("ASC");
        when(configurationManager.getDatabase()).thenReturn(databaseConfig);
        when(configurationManager.getGlobalConfig()).thenReturn(globalConfig);
        when(plugin.getConfigurationManager()).thenReturn(configurationManager);
        when(plugin.getDatabaseManager()).thenReturn(databaseManager);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestDatabase"));
        when(databaseManager.getConnection()).thenAnswer(invocation -> connection());

        auctionQueries = new AuctionQueries(plugin);
        expireQueries = new ExpireQueries(plugin);
        historicQueries = new HistoricQueries(plugin);
        currencyPendingQueries = new CurrencyPendingQueries(plugin);
        importLogQueries = new ImportLogQueries(plugin);

        when(plugin.getAuctionQueries()).thenReturn(auctionQueries);
        when(plugin.getExpireQueries()).thenReturn(expireQueries);
        when(plugin.getHistoricQueries()).thenReturn(historicQueries);
        when(plugin.getCurrencyPendingQueries()).thenReturn(currencyPendingQueries);

        for (IDatabaseTable table : new IDatabaseTable[]{auctionQueries, expireQueries, historicQueries, currencyPendingQueries, importLogQueries}) {
            createTable(table.getTable());
        }
    }

    public static TestDatabase create(Mode mode) throws SQLException {
        return new TestDatabase(mode);
    }

    public Mode mode() {
        return mode;
    }

    public Connection connection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    /**
     * The statement of {@code DatabaseManager.initializeTables}. H2 has no {@code CHARACTER SET}
     * clause on a table.
     * <p>
     * In PostgreSQL mode the DDL shipped by the existing tables is not valid PostgreSQL (backticks,
     * {@code BLOB}, {@code LONG}) — true of a real PostgreSQL server as well, where those tables
     * have to be created by hand. They are translated here the way an administrator would ; the DDL
     * of {@code fa_import_log} only needs its backticks removed.
     */
    private void createTable(String[] table) throws SQLException {
        String columns = table[1];
        if (mode == Mode.POSTGRESQL) {
            columns = columns.replace("`", "").replace(" BLOB", " BYTEA").replace(" LONG", " BIGINT");
        }
        String name = mode == Mode.POSTGRESQL ? table[0] : "`" + table[0] + "`";
        try (Statement statement = keepAlive.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + name + " (" + columns + ");");
        }
    }

    public int count(String table) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    @Override
    public void close() throws SQLException {
        keepAlive.close();
    }
}
