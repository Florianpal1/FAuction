package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.DatabaseManager;
import fr.florianpal.fauction.objects.Bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class BidQueries implements IDatabaseTable {

    private final FAuction plugin;

    private final DatabaseManager databaseManager;

    private final GlobalConfig globalConfig;

    private static final String GET_BIDS = "SELECT * FROM bids ORDER BY id ";

    private static final String GET_BID_WITH_ID = "SELECT * FROM bids WHERE id=?";

    private static final String GET_BIDS_BY_UUID = "SELECT * FROM bids WHERE sellerUuid=?";

    private static final String ADD_BID = "INSERT INTO bids (sellerUuid, sellerName, item, startPrice, currentPrice, currentBidderUuid, currentBidderName, startDate, endDate) VALUES(?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE_ITEM = "UPDATE bids set item=? where id=?";

    // The previousPrice guard rejects a concurrent bid that raced ahead of the caller between the
    // read and the write, on top of the ClaimManager reservation taken before this call.
    private static final String UPDATE_BID = "UPDATE bids SET currentPrice=?, currentBidderUuid=?, currentBidderName=? WHERE id=? AND currentPrice=?";

    private static final String DELETE_BID = "DELETE FROM bids WHERE id=?";

    private static final String DELETE_ALL = "DELETE FROM bids";

    private String autoIncrement = "INTEGER PRIMARY KEY AUTO_INCREMENT";

    private String parameters = "DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci";

    public BidQueries(FAuction plugin) {
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

    /**
     * @return the id the database assigned to the new row, -1 if the insert failed and the item has
     * to be given back to the seller. The caller must use this id for its own cache instead of
     * guessing it, for the same reason as AuctionQueries#addAuction.
     */
    public int addBid(UUID sellerUuid, String sellerName, byte[] item, double startPrice, Date startDate, Date endDate) {

        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(ADD_BID, Statement.RETURN_GENERATED_KEYS)) {

                statement.setString(1, sellerUuid.toString());
                statement.setString(2, sellerName);
                statement.setBytes(3, item);
                statement.setDouble(4, startPrice);
                statement.setDouble(5, startPrice);
                statement.setNull(6, java.sql.Types.VARCHAR);
                statement.setNull(7, java.sql.Types.VARCHAR);
                statement.setLong(8, startDate.getTime());
                statement.setLong(9, endDate.getTime());
                if (statement.executeUpdate() == 0) {
                    return -1;
                }
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : -1;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when add bid. Error {} ", e.getMessage()));
        }
        return -1;
    }

    public void updateItem(int id, byte[] item) {

        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(UPDATE_ITEM)) {

                statement.setBytes(1, item);
                statement.setInt(2, id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when update bid. Error {} ", e.getMessage()));
        }
    }

    /**
     * @return true if this call is the one that raised the bid, from previousPrice to newPrice. The
     * previousPrice guard makes the database refuse a write based on a stale read, so a bid placed
     * against an already-outdated price is rejected instead of silently overwriting a higher bid.
     */
    public boolean updateBid(int id, double previousPrice, double newPrice, UUID bidderUuid, String bidderName) {

        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(UPDATE_BID)) {

                statement.setDouble(1, newPrice);
                statement.setString(2, bidderUuid.toString());
                statement.setString(3, bidderName);
                statement.setInt(4, id);
                statement.setDouble(5, previousPrice);
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when update bid price. Error {} ", e.getMessage()));
        }
        return false;
    }

    /**
     * @return true if this call is the one that removed the row. The database serializes the
     * concurrent deletes of the same row, so exactly one caller gets true whatever the number of
     * packets received at the same time.
     */
    public boolean deleteBid(int id) {

        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(DELETE_BID)) {

                statement.setInt(1, id);
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when delete bid. Error {} ", e.getMessage()));
        }
        return false;
    }

    public void deleteAll() {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when delete all bids from database. Error {} ", e.getMessage()));
        }
    }

    public List<Bid> getBids() {

        ArrayList<Bid> bids = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(GET_BIDS + this.globalConfig.getOrderBy())) {

                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        bids.add(mapRow(result));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get all bids. Error {} ", e.getMessage()));
        }
        return bids;
    }

    public List<Bid> getBids(UUID sellerUuid) {
        ArrayList<Bid> bids = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_BIDS_BY_UUID)) {

                statement.setString(1, sellerUuid.toString());
                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        bids.add(mapRow(result));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get bids by seller uuid. Error {} ", e.getMessage()));
        }
        return bids;
    }

    public Bid getBid(int id) {
        Bid bid = null;
        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(GET_BID_WITH_ID)) {

                statement.setInt(1, id);
                try (ResultSet result = statement.executeQuery()) {

                    if (result.next()) {
                        bid = mapRow(result);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get bid by id. Error {} ", e.getMessage()));
        }
        return bid;
    }

    private Bid mapRow(ResultSet result) throws SQLException {
        int id = result.getInt(1);
        UUID sellerUuid = UUID.fromString(result.getString(2));
        String sellerName = result.getString(3);
        byte[] item = result.getBytes(4);
        double startPrice = result.getDouble(5);
        double currentPrice = result.getDouble(6);
        String currentBidderUuidRaw = result.getString(7);
        UUID currentBidderUuid = currentBidderUuidRaw == null ? null : UUID.fromString(currentBidderUuidRaw);
        String currentBidderName = result.getString(8);
        long startDate = result.getLong(9);
        long endDate = result.getLong(10);

        return new Bid(id, sellerUuid, sellerName, item, startPrice, currentPrice, currentBidderUuid, currentBidderName, startDate, endDate);
    }

    @Override
    public String[] getTable() {
        return new String[]{"bids",
                "`id` " + autoIncrement + ", " +
                        "`sellerUuid` VARCHAR(36) NOT NULL, " +
                        "`sellerName` VARCHAR(36) NOT NULL, " +
                        "`item` BLOB NOT NULL, " +
                        "`startPrice` DOUBLE NOT NULL, " +
                        "`currentPrice` DOUBLE NOT NULL, " +
                        "`currentBidderUuid` VARCHAR(36), " +
                        "`currentBidderName` VARCHAR(36), " +
                        "`startDate` LONG NOT NULL, " +
                        "`endDate` LONG NOT NULL",
                parameters
        };
    }
}
