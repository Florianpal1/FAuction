package fr.florianpal.fauction.api.importer;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An item currently for sale in the source plugin.
 *
 * @param sourceId   stable identifier of the row in the source (SQL id, YAML key, UUID...) : two
 *                   reads of the same source must give the same id, it is what prevents a second
 *                   import from duplicating the row.
 * @param sellerUuid the seller.
 * @param sellerName the seller's name, or {@code null} to let FAuction resolve it from the UUID.
 *                   Colour codes are removed and the name is cut to 36 characters.
 * @param item       the item for sale, the whole stack.
 * @param price      the price of the whole stack, in the source currency.
 * @param listedAt   when the item was put on sale ; FAuction's own expiration counts from it.
 * @param currency   the name of the currency on the source side ({@code "Vault"},
 *                   {@code "CoinsEngine-coins"}...), or {@code null} for the main currency of the
 *                   server. A sale in another currency than the one of FAuction is refused (or given
 *                   back to the seller, see {@code --other-currency}).
 */
public record ImportedAuction(String sourceId, UUID sellerUuid, @Nullable String sellerName, ImportedItem item,
                              double price, Instant listedAt, @Nullable String currency) implements ImportedRow {

    @Override
    public ImportDataType type() {
        return ImportDataType.AUCTION;
    }

    public ImportedAuction {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(sellerUuid, "sellerUuid");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(listedAt, "listedAt");
    }

    public ImportedAuction(String sourceId, UUID sellerUuid, @Nullable String sellerName, ImportedItem item,
                           double price, Instant listedAt) {
        this(sourceId, sellerUuid, sellerName, item, price, listedAt, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String sourceId;
        private UUID sellerUuid;
        private String sellerName;
        private ImportedItem item;
        private double price;
        private Instant listedAt;
        private String currency;

        private Builder() {
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder seller(UUID uuid, @Nullable String name) {
            this.sellerUuid = uuid;
            this.sellerName = name;
            return this;
        }

        public Builder item(ImportedItem item) {
            this.item = item;
            return this;
        }

        public Builder price(double price) {
            this.price = price;
            return this;
        }

        public Builder listedAt(Instant listedAt) {
            this.listedAt = listedAt;
            return this;
        }

        public Builder currency(@Nullable String currency) {
            this.currency = currency;
            return this;
        }

        /**
         * @throws NullPointerException if a mandatory field is missing.
         */
        public ImportedAuction build() {
            return new ImportedAuction(sourceId, sellerUuid, sellerName, item, price, listedAt, currency);
        }
    }
}
