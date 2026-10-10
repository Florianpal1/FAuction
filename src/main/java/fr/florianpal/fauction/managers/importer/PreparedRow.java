package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.queries.CurrencyPendingRow;
import fr.florianpal.fauction.queries.HistoricRow;
import fr.florianpal.fauction.queries.ItemRow;

/**
 * A row that passed the validation, ready to be written.
 *
 * @param sourceType     the type the module pushed it as : the key of the journal, whatever the
 *                       table the row lands in.
 * @param target         the table it is written to.
 * @param row            {@link ItemRow}, {@link HistoricRow} or {@link CurrencyPendingRow}, following
 *                       {@code target}.
 * @param movedToExpires a sale given back to its seller instead of being put on sale.
 * @param note           why it was moved, for the report ; {@code null} otherwise.
 */
public record PreparedRow(ImportDataType sourceType, String sourceId, Target target, Object row,
                          boolean movedToExpires, String note) {

    public enum Target {
        AUCTIONS("auctions"),
        EXPIRES("expires"),
        HISTORIC("fa_auctions_historic"),
        CURRENCY_PENDING("fa_currency_pending");

        private final String table;

        Target(String table) {
            this.table = table;
        }

        public String table() {
            return table;
        }
    }

    /**
     * What identifies the row across runs. A sale and an item waiting to be collected share their
     * identifiers : a module may see the same item as one then as the other.
     */
    public String key() {
        return key(sourceType, sourceId);
    }

    public static String key(ImportDataType type, String sourceId) {
        String namespace = type == ImportDataType.EXPIRED ? ImportDataType.AUCTION.name() : type.name();
        return namespace + ':' + sourceId;
    }
}
