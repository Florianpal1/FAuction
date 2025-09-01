package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.DatabaseManager;
import fr.florianpal.fauction.objects.Auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ExpireQueries implements IDatabaseTable {

    private final FAuction plugin;

    private final DatabaseManager databaseManager;

    private final GlobalConfig globalConfig;

    private static final String GET_EXPIRES = "SELECT * FROM expires ORDER BY id ";

    private static final String GET_EXPIRE_WITH_ID = "SELECT * FROM expires WHERE id=?";

    private static final String GET_EXPIRE_BY_UUID = "SELECT * FROM expires WHERE playerUuid=?";

    private static final String ADD_EXPIRE = "INSERT INTO expires (playerUuid, playerName, item, price, date) VALUES(?,?,?,?,?)";

    private static final String UPDATE_ITEM = "UPDATE expires set item=? where id=?";

    private static final String DELETE_EXPIRE = "DELETE FROM expires WHERE id=?";

    private static final String DELETE_ALL = "DELETE FROM expires";

    private String autoIncrement = "INTEGER PRIMARY KEY AUTO_INCREMENT";

    private String parameters = "DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci";

    public ExpireQueries(FAuction plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.globalConfig = plugin.getConfigurationManager().getGlobalConfig();
        if (plugin.getConfigurationManager().getDatabase().getSqlType() == SQLType.SQLite) {
            autoIncrement = "INTEGER PRIMARY KEY AUTOINCREMENT";
            parameters = "";
        } else if (plugin.getConfigurationManager().getDatabase().getSqlType() == SQLType.PostgreSQL) {
            autoIncrement = "SERIAL PRIMARY KEY";
            parameters = "";
        }
    }

    public void addExpire(UUID playerUUID, String playerName, byte[] item, double price, Date date) {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(ADD_EXPIRE)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, playerName);
                statement.setBytes(3, item);
                statement.setDouble(4, price);
                statement.setLong(5, date.getTime());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when add expired auction to database. Error {} ", e.getMessage()));
        }
    }

    public void updateItem(int id, byte[] item) {

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_ITEM)) {
                statement.setBytes(1, item);
                statement.setInt(2, id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when update expired auction to database. Error {} ", e.getMessage()));
        }
    }

    public void deleteExpire(int id) {

        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(DELETE_EXPIRE)) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when delete expired auction to database. Error {} ", e.getMessage()));
        }
    }

    public void deleteAll() {

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when delete all expired auction to database. Error {} ", e.getMessage()));
        }
    }

    public Map<Integer, byte[]> getExpiresBrut() {

        Map<Integer, byte[]> auctions = new HashMap<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_EXPIRES + globalConfig.getOrderBy())) {

                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        int id = result.getInt(1);

                        byte[] item = result.getBytes(4);
                        auctions.put(id, item);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get expired auction from database. Error {} ", e.getMessage()));
        }
        return auctions;
    }

    public List<Auction> getExpires() {
        List<Auction> auctions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_EXPIRES + globalConfig.getOrderBy())) {

                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        int id = result.getInt(1);
                        UUID playerUuid = UUID.fromString(result.getString(2));
                        String playerName = result.getString(3);
                        byte[] item = result.getBytes(4);
                        double price = result.getDouble(5);
                        long date = result.getLong(6);


                        auctions.add(new Auction(id, playerUuid, playerName, price, item, date));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get expired auctions from database. Error {} ", e.getMessage()));
        }
        return auctions;
    }

    public List<Auction> getExpires(UUID playerUuid) {

        ArrayList<Auction> auctions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_EXPIRE_BY_UUID)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        int id = result.getInt(1);
                        String playerName = result.getString(3);
                        byte[] item = result.getBytes(4);
                        double price = result.getDouble(5);
                        long date = result.getLong(6);

                        auctions.add(new Auction(id, playerUuid, playerName, price, item, date));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get expired auctions by uuid from database. Error {} ", e.getMessage()));
        }
        return auctions;
    }

    public Auction getExpire(int id) {

        Auction auction = null;
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_EXPIRE_WITH_ID)) {
                statement.setInt(1, id);
                try (ResultSet result = statement.executeQuery()) {

                    if (result.next()) {
                        UUID playerUuid = UUID.fromString(result.getString(2));
                        String playerName = result.getString(3);
                        byte[] item = result.getBytes(4);
                        double price = result.getDouble(5);
                        long date = result.getLong(6);


                        auction = new Auction(id, playerUuid, playerName, price, item, date);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get auction by id for database. Error {} ", e.getMessage()));
        }
        return auction;
    }

    @Override
    public String[] getTable() {
        return new String[]{"expires",
                "`id` " + autoIncrement + ", " +
                        "`playerUuid` VARCHAR(36) NOT NULL, " +
                        "`playerName` VARCHAR(36) NOT NULL, " +
                        "`item` BLOB NOT NULL, " +
                        "`price` DOUBLE NOT NULL, " +
                        "`date` LONG NOT NULL",
                parameters
        };
    }
}
