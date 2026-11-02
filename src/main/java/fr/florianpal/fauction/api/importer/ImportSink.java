package fr.florianpal.fauction.api.importer;

/**
 * Where a module pushes what it reads, one row at a time. Provided by FAuction.
 * <p>
 * The module never writes to the database itself : FAuction validates each row, ignores the rows a
 * previous import already brought, writes the others in transactional batches and reports the rows it
 * refused with their reason. A module can therefore push rows as it reads them, without keeping them
 * in memory.
 * <p>
 * Called from the import thread only (the one running {@link DataImporter#read}).
 */
public interface ImportSink {

    void accept(ImportedAuction auction);

    void accept(ImportedExpired expired);

    void accept(ImportedHistoric historic);

    void accept(ImportedPendingCurrency pendingCurrency);

    /**
     * A row of the source the module cannot (or chooses not to) convert : unreadable, deleted by an
     * administrator, excluded by an option... It is listed in the report with its reason, so that no
     * item nor money disappears without a trace.
     */
    void skip(String sourceId, String reason);

    /**
     * Whether the administrator cancelled the import. To be tested in the reading loops : once it is
     * true, the rows pushed are ignored anyway.
     */
    boolean isCancelled();
}
