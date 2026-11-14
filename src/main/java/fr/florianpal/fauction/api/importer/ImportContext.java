package fr.florianpal.fauction.api.importer;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * What a module knows of the import being run. Provided by FAuction ; not meant to be implemented by
 * modules (methods may be added). In tests, use
 * {@link fr.florianpal.fauction.api.importer.testing.TestImportContext}.
 */
public interface ImportContext {

    /**
     * A logger whose messages are prefixed with {@code [Import:<id>]}.
     */
    Logger logger();

    /**
     * The options of the module given on the command line ({@code --opt key=value}), keys in lower
     * case. Unknown keys are the module's to refuse or ignore.
     */
    Map<String, String> options();

    /**
     * Whether this is a dry run. Nothing is written either way by the module ; FAuction validates
     * the rows and reports what would have been imported.
     */
    boolean dryRun();

    /**
     * The types the administrator asked for ({@code --types}), among the ones the module supports.
     * The rows of another type are ignored by FAuction ; testing this only spares the module some
     * reading.
     */
    Set<ImportDataType> requestedTypes();

    /**
     * Whether the auctions with bids of the source have to be converted (items given back, bidders
     * refunded...) or skipped ({@code --bids=skip}). Only meaningful to a source with bids.
     */
    boolean convertBids();

    /**
     * The clock to read "now" from, so that the expiration computations of a module can be tested at
     * a fixed date.
     */
    Clock clock();

    /**
     * The {@code plugins/} folder of the server.
     */
    File pluginsFolder();

    /**
     * The source plugin, if it is loaded (enabled or not).
     */
    Optional<Plugin> sourcePlugin(String name);

    ImportHelpers helpers();
}
