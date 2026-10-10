package fr.florianpal.fauction.api.importer;

/**
 * Version of the import API.
 * <p>
 * Compatibility policy : a new data type is added as a new {@link ImportDataType} constant, a new
 * record and a new {@code default} method of {@link ImportSink}, so the modules written against an
 * older version keep compiling and running ; the version is then increased. The signature of an
 * existing record never changes.
 */
public final class ImporterApi {

    /**
     * The version of the API this FAuction provides. A module whose
     * {@link DataImporter#requiredApiVersion()} is higher is refused at registration.
     */
    public static final int API_VERSION = 1;

    private ImporterApi() {
    }
}
