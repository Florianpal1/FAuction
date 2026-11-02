package fr.florianpal.fauction.api.importer;

/**
 * Thrown by a {@link RowMapper} or a {@link SectionMapper} to skip the current row with a reason :
 * the base classes turn it into {@link ImportSink#skip}.
 */
public class ImportSkipException extends Exception {

    private final String sourceId;

    public ImportSkipException(String sourceId, String reason) {
        super(reason);
        this.sourceId = sourceId;
    }

    public String getSourceId() {
        return sourceId;
    }
}
