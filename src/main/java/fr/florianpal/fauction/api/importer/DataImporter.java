package fr.florianpal.fauction.api.importer;

import java.util.Set;

/**
 * An import module : reads the data of another auction house plugin and pushes it into an
 * {@link ImportSink}. The interface to implement.
 * <p>
 * Rules :
 * <ul>
 *     <li>never write to the source, it is read only ;</li>
 *     <li>give every row a stable {@code sourceId}, unique per data type ;</li>
 *     <li>test {@link ImportSink#isCancelled()} in the reading loops ;</li>
 *     <li>a row that cannot be converted is {@link ImportSink#skip skipped}, not thrown : an exception
 *     out of {@link #read} stops the whole import ;</li>
 *     <li>{@link #read} runs off the main thread ; use {@link ImportHelpers#callSync} for the Bukkit
 *     API calls that need it.</li>
 * </ul>
 */
public interface DataImporter {

    /**
     * Unique identifier, used on the command line : 1 to 32 characters among {@code [a-z0-9_-]}.
     */
    String id();

    /**
     * Name shown to the administrator, the name of the source plugin and its tested version for
     * instance.
     */
    String displayName();

    Set<ImportDataType> supportedTypes();

    /**
     * Whether the source can be read now. Called off the main thread, before every import with the
     * options of the command, and by {@code /ah admin import list} with no option. Must refuse while
     * the source plugin is enabled : it keeps its data in memory, and would still hand out the items
     * and the money just imported.
     */
    ImportAvailability checkAvailability(ImportContext context);

    /**
     * Reads the source and pushes every row into {@code sink}. Called off the main thread, once per
     * import.
     *
     * @throws Exception a failure that prevents reading the source at all (unreadable file, database
     *                   down...). The rows pushed before are still imported.
     */
    void read(ImportContext context, ImportSink sink) throws Exception;

    /**
     * The version of the import API this module needs. Override it with
     * {@code return ImporterApi.API_VERSION;} : the constant is copied into the module when it is
     * compiled, so a module built against a newer FAuction is refused by an older one at registration
     * instead of failing in the middle of an import.
     */
    default int requiredApiVersion() {
        return 1;
    }
}
