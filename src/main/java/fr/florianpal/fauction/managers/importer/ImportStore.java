package fr.florianpal.fauction.managers.importer;

import java.util.List;
import java.util.Set;

/**
 * Where the validated rows go.
 */
public interface ImportStore {

    /**
     * Writes a batch, in a single transaction with its journal entries : either the whole batch is
     * written, or nothing is.
     *
     * @param forceReimport ignore (and replace) the journal entries of the rows.
     * @param dryRun        only look the rows up in the journal, write nothing.
     * @return the {@link PreparedRow#key() keys} of the rows a previous import already brought, which
     * were not written.
     * @throws Exception if the batch was refused ; nothing of it was written.
     */
    Set<String> write(String importerId, List<PreparedRow> rows, boolean forceReimport, boolean dryRun) throws Exception;
}
