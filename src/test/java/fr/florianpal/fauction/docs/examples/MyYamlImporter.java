package fr.florianpal.fauction.docs.examples;

import fr.florianpal.fauction.api.importer.AbstractYamlImporter;
import fr.florianpal.fauction.api.importer.ImportAvailability;
import fr.florianpal.fauction.api.importer.ImportContext;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportSkipException;
import fr.florianpal.fauction.api.importer.ImporterApi;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedItem;

import java.io.File;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Example of the wiki page Importer-API : a complete module for an imaginary plugin "MyAH" that keeps
 * its sales in {@code plugins/MyAH/listings.yml} :
 * <pre>
 * listings:
 *   "17":
 *     seller: 8667ba71-b85a-4004-af54-457a9734eed7
 *     item: rO0ABXNy...   # BukkitObjectOutputStream, base64
 *     price: 250.0
 *     created: 1760000000000
 * expired:
 *   "3": ...
 * </pre>
 * Compiled and run by the tests : an example of the wiki that no longer compiles breaks the build.
 */
public class MyYamlImporter extends AbstractYamlImporter {

    @Override
    public String id() {
        return "myah";
    }

    @Override
    public String displayName() {
        return "MyAH 1.0";
    }

    @Override
    public Set<ImportDataType> supportedTypes() {
        return EnumSet.of(ImportDataType.AUCTION, ImportDataType.EXPIRED);
    }

    @Override
    public int requiredApiVersion() {
        return ImporterApi.API_VERSION;
    }

    @Override
    public ImportAvailability checkAvailability(ImportContext context) {
        // The source must be stopped : it would keep selling the items being imported.
        if (context.sourcePlugin("MyAH").map(plugin -> plugin.isEnabled()).orElse(false)) {
            return ImportAvailability.unavailable("MyAH is still enabled, remove it first");
        }
        File file = file(context);
        return file.isFile()
                ? ImportAvailability.available("Found " + file.getPath())
                : ImportAvailability.unavailable("No " + file.getPath());
    }

    @Override
    protected File file(ImportContext context) {
        return new File(context.helpers().sourceDataFolder("MyAH"), "listings.yml");
    }

    @Override
    protected List<YamlSource<?>> sources(ImportContext context) {
        return List.of(
                new YamlSource<>(ImportDataType.AUCTION, "listings", (key, section, ctx) -> {
                    String seller = section.getString("seller");
                    if (seller == null) {
                        // Kept in the report with its reason, the import goes on.
                        throw new ImportSkipException(key, "No seller");
                    }
                    return new ImportedAuction(
                            key,                                              // stable sourceId
                            UUID.fromString(seller),
                            null,                                             // FAuction resolves the name
                            ImportedItem.ofBukkitBase64(section.getString("item")),
                            section.getDouble("price"),
                            Instant.ofEpochMilli(section.getLong("created")));
                }),
                new YamlSource<>(ImportDataType.EXPIRED, "expired", (key, section, ctx) ->
                        new ImportedExpired(key, UUID.fromString(section.getString("owner")), null,
                                ImportedItem.ofBukkitBase64(section.getString("item")), 0, ctx.clock().instant())));
    }
}
