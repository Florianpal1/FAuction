package fr.florianpal.fauction.docs.examples;

import fr.florianpal.fauction.api.importer.AbstractSqlImporter;
import fr.florianpal.fauction.api.importer.ImportAvailability;
import fr.florianpal.fauction.api.importer.ImportContext;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedItem;
import fr.florianpal.fauction.api.importer.ImportedPendingCurrency;

import java.sql.Connection;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Example of the wiki page Importer-API : a module reading the SQL database of an imaginary plugin,
 * whose address it takes from the options of the command ({@code --opt url=jdbc:...}).
 * Compiled and run by the tests.
 */
public class MySqlImporter extends AbstractSqlImporter {

    @Override
    public String id() {
        return "mysqlah";
    }

    @Override
    public String displayName() {
        return "MySqlAH 2.x";
    }

    @Override
    public Set<ImportDataType> supportedTypes() {
        return EnumSet.of(ImportDataType.HISTORIC, ImportDataType.PENDING_CURRENCY);
    }

    @Override
    public ImportAvailability checkAvailability(ImportContext context) {
        return context.options().containsKey("url")
                ? ImportAvailability.available("Database " + context.options().get("url"))
                : ImportAvailability.unavailable("Give the database with --opt url=jdbc:...");
    }

    @Override
    protected Connection openConnection(ImportContext context) throws Exception {
        return context.helpers().openJdbc(context.options().get("url"),
                context.options().getOrDefault("user", ""), context.options().getOrDefault("password", ""));
    }

    @Override
    protected List<SqlSource<?>> sources(ImportContext context) {
        return List.of(
                new SqlSource<>(ImportDataType.HISTORIC,
                        "SELECT id, seller, buyer, item, price, sold_at FROM sales ORDER BY id",
                        (row, ctx) -> new ImportedHistoric(
                                "sale-" + row.getLong("id"),
                                UUID.fromString(row.getString("seller")), null,
                                UUID.fromString(row.getString("buyer")), null,
                                ImportedItem.ofBukkitBase64(row.getString("item")),
                                row.getDouble("price"),
                                null,                                         // listing date not kept
                                Instant.ofEpochSecond(row.getLong("sold_at")))),
                new SqlSource<>(ImportDataType.PENDING_CURRENCY,
                        "SELECT player, amount FROM unpaid",
                        (row, ctx) -> new ImportedPendingCurrency(
                                "unpaid-" + row.getString("player"),
                                UUID.fromString(row.getString("player")),
                                "Vault",                                      // mapped by import.currency-map
                                row.getDouble("amount"),
                                "Sale not collected")));
    }
}
