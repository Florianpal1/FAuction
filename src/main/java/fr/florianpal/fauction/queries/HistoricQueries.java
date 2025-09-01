package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.DatabaseManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Historic;
import fr.florianpal.fauction.utils.SerializationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class HistoricQueries implements IDatabaseTable {

    private final FAuction plugin;

    private final DatabaseManager databaseManager;

    private final GlobalConfig globalConfig;

    private static final String GET_HISTORICS = "SELECT * FROM fa_auctions_historic ORDER BY id ";
    private static final String GET_HISTORIC_WITH_ID = "SELECT * FROM fa_auctions_historic WHERE id=?";
    private static final String GET_HISTORICS_BY_UUID = "SELECT * FROM fa_auctions_historic WHERE playerUuid=?";
    private static final String ADD_HISTORIC = "INSERT INTO fa_auctions_historic (playerUuid, playerName, playerBuyerUuid, playerBuyerName, item, price, date, buyDate) VALUES(?,?,?,?,?,?,?,?)";
    private static final String DELETE_ALL = "DELETE FROM fa_auctions_historic";
    private static final String ALTER_BUY_DATE = "ALTER TABLE fa_auctions_historic ADD buyDate long;";

    private String autoIncrement = "INTEGER PRIMARY KEY AUTO_INCREMENT";

    private String parameters = "DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci";

    public HistoricQueries(FAuction plugin) {
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

    public void addHistoric(UUID playerUUID, String playerName, UUID playerBuyerUUID, String playerBuyerName, byte[] item, double price, Date date) {

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(ADD_HISTORIC)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, playerName);
                statement.setString(3, playerBuyerUUID.toString());
                statement.setString(4, playerBuyerName);
                statement.setBytes(5, item);
                statement.setDouble(6, price);
                statement.setLong(7, date.getTime());
                statement.setLong(7, new Date().getTime());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when add auction. Error {} ", e.getMessage()));
        }
    }

    public void addHistoric(Auction auction, UUID playerBuyerUUID, String playerBuyerName) {

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(ADD_HISTORIC)) {
                statement.setString(1, auction.getPlayerUUID().toString());
                statement.setString(2, auction.getPlayerName());
                statement.setString(3, playerBuyerUUID.toString());
                statement.setString(4, playerBuyerName);
                statement.setBytes(5, SerializationUtil.serialize(auction.getItemStack()));
                statement.setDouble(6, auction.getPrice());
                statement.setLong(7, auction.getDate().getTime());
                statement.setLong(8, new Date().getTime());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when add auction. Error {} ", e.getMessage()));
        }
    }

    public Map<Integer, byte[]> getHistoricBrut() {

        Map<Integer, byte[]> auctions = new HashMap<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_HISTORICS + this.globalConfig.getOrderBy())) {

                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        int id = result.getInt(1);

                        byte[] item = result.getBytes(6);
                        auctions.put(id, item);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get all auctions. Error {} ", e.getMessage()));
        }
        return auctions;
    }

    public List<Historic> getHistorics() {

        ArrayList<Historic> auctions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_HISTORICS + this.globalConfig.getOrderBy())) {

                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        int id = result.getInt(1);
                        UUID playerUUID = UUID.fromString(result.getString(2));
                        String playerName = result.getString(3);
                        UUID playerBuyerUUID = UUID.fromString(result.getString(4));
                        String playerBuyerName = result.getString(5);

                        byte[] item = result.getBytes(6);
                        double price = result.getDouble(7);
                        long date = result.getLong(8);
                        long buyDate = result.getLong(9);

                        auctions.add(new Historic(id, playerUUID, playerName, playerBuyerUUID, playerBuyerName, price, item, date, buyDate));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get all auction. Error {} ", e.getMessage()));
        }
        return auctions;
    }

    public List<Historic> getHistorics(UUID playerUuid) {

        ArrayList<Historic> auctions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_HISTORICS_BY_UUID)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {

                        int id = result.getInt(1);
                        UUID playerUUID = UUID.fromString(result.getString(2));
                        String playerName = result.getString(3);
                        UUID playerBuyerUUID = UUID.fromString(result.getString(4));
                        String playerBuyerName = result.getString(5);

                        byte[] item = result.getBytes(6);
                        double price = result.getDouble(7);
                        long date = result.getLong(8);
                        long buyDate = result.getLong(9);

                        auctions.add(new Historic(id, playerUUID, playerName, playerBuyerUUID, playerBuyerName, price, item, date, buyDate));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get auction by player uuid. Error {} ", e.getMessage()));
        }
        return auctions;
    }

    public Auction getHistoric(int id) {

        Auction auction = null;
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_HISTORIC_WITH_ID)) {
                statement.setInt(1, id);
                try (ResultSet result = statement.executeQuery()) {

                    if (result.next()) {
                        UUID playerUUID = UUID.fromString(result.getString(2));
                        String playerName = result.getString(3);
                        UUID playerBuyerUUID = UUID.fromString(result.getString(4));
                        String playerBuyerName = result.getString(5);

                        byte[] item = result.getBytes(6);
                        double price = result.getDouble(7);
                        long date = result.getLong(8);
                        long buyDate = result.getLong(9);

                        auction = new Historic(id, playerUUID, playerName, playerBuyerUUID, playerBuyerName, price, item, date, buyDate);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get auction by id. Error {} ", e.getMessage()));
        }
        return auction;
    }

    public void deleteAll() {

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when delete all historic to database. Error {} ", e.getMessage()));
        }
    }

    public void addBuyDate() {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(ALTER_BUY_DATE)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when delete all historic to database. Error {} ", e.getMessage()));
        }
    }

    @Override
    public String[] getTable() {
        return new String[]{"fa_auctions_historic",
                "`id` " + autoIncrement + ", " +
                        "`playerUuid` VARCHAR(36) NOT NULL, " +
                        "`playerName` VARCHAR(36) NOT NULL, " +
                        "`playerBuyerUuid` VARCHAR(36) NOT NULL, " +
                        "`playerBuyerName` VARCHAR(36) NOT NULL, " +
                        "`item` BLOB NOT NULL, " +
                        "`price` DOUBLE NOT NULL, " +
                        "`date` LONG NOT NULL, " +
                        "`buyDate` LONG",
                parameters
        };
    }
}
