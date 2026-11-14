package fr.florianpal.fauction.api.importer;

/**
 * Hands a row produced by a mapper to the right method of the sink.
 */
final class ImportSinks {

    private ImportSinks() {
    }

    /**
     * @param expected the type the source declares ; a mapper producing another one is a bug of the
     *                 module, reported on its row rather than imported under the wrong type.
     * @param row      {@code null} to ignore the row.
     * @throws IllegalArgumentException if the row is of another type, or of a type this FAuction does
     *                                  not know.
     */
    static void emit(ImportSink sink, ImportDataType expected, ImportedRow row) {
        if (row == null) {
            return;
        }
        if (row.type() != expected) {
            throw new IllegalArgumentException("The mapper of the " + expected.id() + " rows produced a " + row.type().id() + " row");
        }
        switch (row) {
            case ImportedAuction auction -> sink.accept(auction);
            case ImportedExpired expired -> sink.accept(expired);
            case ImportedHistoric historic -> sink.accept(historic);
            case ImportedPendingCurrency pending -> sink.accept(pending);
            default -> throw new IllegalArgumentException("Not an imported row : " + row.getClass().getName());
        }
    }

    static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
