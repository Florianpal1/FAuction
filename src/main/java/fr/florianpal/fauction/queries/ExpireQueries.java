
/*
 * Copyright (C) 2022 Florianpal
 *
 * This program is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses/>.
 *
 * Last modification : 07/01/2022 23:07
 *
 *  @author Florianpal.
 */

package fr.florianpal.fauction.queries;

import co.aikar.taskchain.TaskChain;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.IDatabaseTable;
import fr.florianpal.fauction.managers.DatabaseManager;
import fr.florianpal.fauction.objects.Auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ExpireQueries implements IDatabaseTable {
    private static final String GET_AUCTIONS = "SELECT * FROM expires";
    private static final String GET_AUCTION_WITH_ID = "SELECT * FROM expires WHERE id=?";
    private static final String GET_AUCTIONS_BY_UUID = "SELECT * FROM expires WHERE playerUuid=?";
    private static final String ADD_AUCTION = "INSERT INTO expires (playerUuid, playerName, item, price, date) VALUES(?,?,?,?,?)";
    private static final String DELETE_AUCTION = "DELETE FROM expires WHERE id=?";

    private final DatabaseManager databaseManager;

    public ExpireQueries(FAuction plugin) {
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void addAuction(UUID playerUUID, String playerName, byte[] item, double price, Date date){
        final TaskChain<Void> chain = FAuction.newChain();
        chain.asyncFirst(() -> {
            PreparedStatement statement = null;
            try (Connection connection = databaseManager.getConnection()) {
                statement = connection.prepareStatement(ADD_AUCTION);
                statement.setString(1, playerUUID.toString());
                statement.setString(2, playerName);
                statement.setBytes(3, item);
                statement.setDouble(4, price);
                statement.setLong(5, date.getTime());
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (statement != null) {
                        statement.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }).execute();
    }

    public void deleteAuctions(int id) {
        final TaskChain<Void> chain = FAuction.newChain();
        chain.asyncFirst(() -> {
            PreparedStatement statement = null;
            try (Connection connection = databaseManager.getConnection()) {
                statement = connection.prepareStatement(DELETE_AUCTION);
                statement.setInt(1, id);
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (statement != null) {
                    try {
                        statement.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }).execute();
    }

    public TaskChain<ArrayList<Auction>> getExpires() {
        List<Auction> auctions = new ArrayList<>();
        final TaskChain<ArrayList<Auction>> chain = FAuction.getTaskChainFactory().newSharedChain("getExpires");
        chain.asyncFirst(() -> {
            PreparedStatement statement = null;
            ResultSet result = null;
            try (Connection connection = databaseManager.getConnection()) {
                statement = connection.prepareStatement(GET_AUCTIONS);

                result = statement.executeQuery();

                while (result.next()) {
                    int id = result.getInt(1);
                    UUID playerUuid = UUID.fromString(result.getString(2));
                    String playerName = result.getString(3);
                    byte[] item = result.getBytes(4);
                    double price = result.getDouble(5);
                    long date = result.getLong(6);


                    auctions.add(new Auction(id, playerUuid, playerName, price, item, date));
                }
                chain.setTaskData("expires", auctions);
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (result != null) {
                        result.close();
                    }
                    if (statement != null) {
                        statement.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return chain;
        });
        return chain;
    }

    public TaskChain<ArrayList<Auction>> getExpires(UUID playerUuid) {

        final TaskChain<ArrayList<Auction>> chain = FAuction.getTaskChainFactory().newSharedChain("getExpires");
        chain.asyncFirst(() -> {
            PreparedStatement statement = null;
            ResultSet result = null;
            ArrayList<Auction> auctions = new ArrayList<>();
            try (Connection connection = databaseManager.getConnection()) {
                statement = connection.prepareStatement(GET_AUCTIONS_BY_UUID);
                statement.setString(1, playerUuid.toString());
                result = statement.executeQuery();

                while (result.next()) {
                    int id = result.getInt(1);
                    String playerName = result.getString(3);
                    byte[] item = result.getBytes(4);
                    double price = result.getDouble(5);
                    long date = result.getLong(6);


                    auctions.add(new Auction(id, playerUuid, playerName, price, item, date));
                }
                chain.setTaskData("expires", auctions);
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (result != null) {
                        result.close();
                    }
                    if (statement != null) {
                        statement.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return chain;
        });
        return chain;
    }

    public TaskChain<Auction> getExpire(int id) {
        final TaskChain<Auction> chain = FAuction.newChain();
        chain.asyncFirst(() -> {
            PreparedStatement statement = null;
            ResultSet result = null;
            try (Connection connection = databaseManager.getConnection()) {
                statement = connection.prepareStatement(GET_AUCTION_WITH_ID);
                statement.setInt(1, id);
                result = statement.executeQuery();

                if (result.next()) {
                    UUID playerUuid = UUID.fromString(result.getString(2));
                    String playerName = result.getString(3);
                    byte[] item = result.getBytes(4);
                    double price = result.getDouble(5);
                    long date = result.getLong(6);


                    chain.setTaskData("expire", new Auction(id, playerUuid, playerName, price, item, date));
                } else {
                    chain.setTaskData("expire", null);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (result != null) {
                        result.close();
                    }
                    if (statement != null) {
                        statement.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return chain;
        });
        return chain;
    }

    @Override
    public String[] getTable() {
        return new String[]{"expires",
                "`id` INTEGER NOT NULL AUTO_INCREMENT, " +
                        "`playerUuid` VARCHAR(36) NOT NULL, " +
                        "`playerName` VARCHAR(36) NOT NULL, " +
                        "`item` BLOB NOT NULL, " +
                        "`price` DOUBLE NOT NULL, " +
                        "`date` LONG NOT NULL," +
                        "PRIMARY KEY (`id`)",
                "DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci"};
    }
}
