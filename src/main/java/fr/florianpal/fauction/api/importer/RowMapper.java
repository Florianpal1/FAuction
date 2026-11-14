package fr.florianpal.fauction.api.importer;

import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;

/**
 * Converts the current row of a {@link ResultSet} into one of the {@code Imported*} records.
 *
 * @param <T> the record produced.
 */
@FunctionalInterface
public interface RowMapper<T extends ImportedRow> {

    /**
     * @return the row to import, or {@code null} to ignore it silently (prefer
     * {@link ImportSkipException}, which keeps a trace in the report).
     * @throws ImportSkipException to skip the row with a reason.
     * @throws Exception           any other failure skips the row too, with the message of the
     *                             exception as reason.
     */
    @Nullable T map(ResultSet row, ImportContext context) throws Exception;
}
