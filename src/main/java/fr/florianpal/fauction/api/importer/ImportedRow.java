package fr.florianpal.fauction.api.importer;

/**
 * A row a module pushes : {@link ImportedAuction}, {@link ImportedExpired}, {@link ImportedHistoric}
 * or {@link ImportedPendingCurrency}. A new data type comes with a new record implementing it.
 */
public interface ImportedRow {

    /**
     * Stable identifier of the row in the source.
     */
    String sourceId();

    /**
     * The data type this row is imported as.
     */
    ImportDataType type();
}
