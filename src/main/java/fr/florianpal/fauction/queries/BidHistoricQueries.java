package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.DatabaseManager;
import fr.florianpal.fauction.objects.Bid;
import fr.florianpal.fauction.objects.BidHistoric;
import fr.florianpal.fauction.utils.SerializationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class BidHistoricQueries implements IDatabaseTable {

    private final FAuction plugin;

    private final DatabaseManager databaseManager;

    private final GlobalConfig globalConfig;

    private static final String GET_BID_HISTORICS = "SELECT * FROM bids_historic ORDER BY id ";
    private static final String GET_BID_HISTORICS_BY_UUID = "SELECT * FROM bids_historic WHERE sellerUuid=?";
    private static final String ADD_BID_HISTORIC = "INSERT INTO bids_historic (sellerUuid, sellerName, buyerUuid, buyerName, item, startPrice, finalPrice, startDate, endDate) VALUES(?,?,?,?,?,?,?,?,?)";
    private static final String DELETE_ALL = "DELETE FROM bids_historic";

    private String autoIncrement = "INTEGER PRIMARY KEY AUTO_INCREMENT";

    private String parameters = "DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci";

    public BidHistoricQueries(FAuction plugin) {
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

    public void addBidHistoric(Bid bid, UUID buyerUuid, String buyerName) {

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(ADD_BID_HISTORIC)) {
                statement.setString(1, bid.getSellerUuid().toString());
                statement.setString(2, bid.getSellerName());
                statement.setString(3, buyerUuid.toString());
                statement.setString(4, buyerName);
                statement.setBytes(5, SerializationUtil.serialize(bid.getItemStack()));
                statement.setDouble(6, bid.getStartPrice());
                statement.setDouble(7, bid.getCurrentPrice());
                statement.setLong(8, bid.getStartDate().getTime());
                statement.setLong(9, new Date().getTime());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when add bid historic. Error {} ", e.getMessage()));
        }
    }

    public List<BidHistoric> getBidHistorics() {

        ArrayList<BidHistoric> bidHistorics = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_BID_HISTORICS + this.globalConfig.getOrderBy())) {

                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        bidHistorics.add(mapRow(result));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get all bid historics. Error {} ", e.getMessage()));
        }
        return bidHistorics;
    }

    public List<BidHistoric> getBidHistorics(UUID sellerUuid) {

        ArrayList<BidHistoric> bidHistorics = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_BID_HISTORICS_BY_UUID)) {
                statement.setString(1, sellerUuid.toString());
                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        bidHistorics.add(mapRow(result));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get bid historics by seller uuid. Error {} ", e.getMessage()));
        }
        return bidHistorics;
    }

    public void deleteAll() {

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when delete all bid historic from database. Error {} ", e.getMessage()));
        }
    }

    private BidHistoric mapRow(ResultSet result) throws SQLException {
        int id = result.getInt(1);
        UUID sellerUuid = UUID.fromString(result.getString(2));
        String sellerName = result.getString(3);
        UUID buyerUuid = UUID.fromString(result.getString(4));
        String buyerName = result.getString(5);
        byte[] item = result.getBytes(6);
        double startPrice = result.getDouble(7);
        double finalPrice = result.getDouble(8);
        long startDate = result.getLong(9);
        long endDate = result.getLong(10);

        return new BidHistoric(id, sellerUuid, sellerName, buyerUuid, buyerName, item, startPrice, finalPrice, startDate, endDate);
    }

    @Override
    public String[] getTable() {
        return new String[]{"bids_historic",
                "`id` " + autoIncrement + ", " +
                        "`sellerUuid` VARCHAR(36) NOT NULL, " +
                        "`sellerName` VARCHAR(36) NOT NULL, " +
                        "`buyerUuid` VARCHAR(36) NOT NULL, " +
                        "`buyerName` VARCHAR(36) NOT NULL, " +
                        "`item` BLOB NOT NULL, " +
                        "`startPrice` DOUBLE NOT NULL, " +
                        "`finalPrice` DOUBLE NOT NULL, " +
                        "`startDate` LONG NOT NULL, " +
                        "`endDate` LONG NOT NULL",
                parameters
        };
    }
}
