package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.DatabaseManager;
import fr.florianpal.fauction.objects.CurrencyPending;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CurrencyPendingQueries implements IDatabaseTable {

    private final FAuction plugin;

    private final DatabaseManager databaseManager;

    private final GlobalConfig globalConfig;

    private static final String GET_CURRENCY_PENDING = "SELECT * FROM fa_currency_pending ORDER BY id ";

    private static final String ADD_CURRENCY_PENDING = "INSERT INTO fa_currency_pending (playerUuid, currencyType, amount) VALUES(?,?,?)";

    private static final String DELETE_CURRENCY_PENDING = "DELETE FROM fa_currency_pending WHERE id=?";


    private String autoIncrement = "INTEGER PRIMARY KEY AUTO_INCREMENT";

    private String parameters = "DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci";

    public CurrencyPendingQueries(FAuction plugin) {
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

    public void addCurrencyPending(UUID playerUUID, CurrencyType currencyType, double amount) {

        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(ADD_CURRENCY_PENDING)){

                statement.setString(1, playerUUID.toString());
                statement.setString(2, currencyType.toString());
                statement.setDouble(3, amount);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when add auction. Error {} ", e.getMessage()));
        }
    }

    public void deleteCurrencyPending(int id) {

        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(DELETE_CURRENCY_PENDING)) {

                statement.setInt(1, id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when delete auction. Error {} ", e.getMessage()));
        }
    }

    public List<CurrencyPending> getCurrencyPending() {

        ArrayList<CurrencyPending> currencyPendings = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(GET_CURRENCY_PENDING + this.globalConfig.getOrderBy())) {

                try (ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        int id = result.getInt(1);
                        UUID playerUuid = UUID.fromString(result.getString(2));
                        CurrencyType currencyType = CurrencyType.valueOf(result.getString(3));
                        double amount = result.getDouble(4);

                        currencyPendings.add(new CurrencyPending(id, playerUuid, currencyType, amount));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe(String.join("Error when get all auction. Error {} ", e.getMessage()));
        }
        return currencyPendings;
    }

    @Override
    public String[] getTable() {
        return new String[]{"fa_currency_pending",
                "`id` " + autoIncrement + ", " +
                        "`playerUuid` VARCHAR(36) NOT NULL, " +
                        "`currencyType` VARCHAR(36) NOT NULL, " +
                        "`amount` DOUBLE NOT NULL",
                parameters
        };
    }
}
