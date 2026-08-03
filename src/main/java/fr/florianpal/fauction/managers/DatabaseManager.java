package fr.florianpal.fauction.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.queries.IDatabaseTable;

import java.sql.*;
import java.util.ArrayList;

public class DatabaseManager {

    private final HikariConfig config = new HikariConfig();

    private final HikariDataSource ds;

    private final FAuction plugin;

    private final ArrayList<IDatabaseTable> repositories = new ArrayList<>();

    public DatabaseManager(FAuction plugin) throws SQLException {
        this.plugin = plugin;

        config.setJdbcUrl(plugin.getConfigurationManager().getDatabase().getUrl());
        config.setUsername(plugin.getConfigurationManager().getDatabase().getUser());
        config.setPassword(plugin.getConfigurationManager().getDatabase().getPassword());

        config.setMaximumPoolSize(plugin.getConfigurationManager().getDatabase().getMaximumPoolSize());

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        if (plugin.getConfigurationManager().getDatabase().getSqlType().equals(SQLType.SQLite)) {
            config.addDataSourceProperty("dataSource.journal_mode", "WAL");
            // SQLite only ever allows one writer at a time; a pool bigger than 1 would let two
            // threads open concurrent connections and fail with "database is locked" instead of
            // simply waiting their turn.
            config.setMaximumPoolSize(1);
        }

        if (plugin.getConfigurationManager().getDatabase().getSqlType().equals(SQLType.MariaDB)) {
            config.setDriverClassName("org.mariadb.jdbc.Driver");
        }

        ds = new HikariDataSource(config);
    }

    public void close() {
        ds.close();
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void addRepository(IDatabaseTable repository) {
        repositories.add(repository);
    }

    public void initializeTables() {
        try (Connection co = getConnection()) {
            for (IDatabaseTable repository : repositories) {
                String[] tableInformation = repository.getTable();

                if (!tableExists(co, tableInformation[0])) {
                    try {
                        Statement statement = co.createStatement();
                        statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + tableInformation[0] + "` (" + tableInformation[1] + ") " + tableInformation[2] + ";");
                        plugin.getLogger().info("The table " + tableInformation[0] + " did not exist and was created !");
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Unable to create table " + tableInformation[0] + " !");
                        e.printStackTrace();
                    }
                }
            }

            plugin.getLogger().info("Initialized database tables");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean tableExists(Connection co, String tableName) throws SQLException {
        DatabaseMetaData dbm = co.getMetaData();
        ResultSet tables = dbm.getTables(null, null, tableName, null);

        return tables.next();

    }
}
