package fr.florianpal.fauction.api.importer;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A sale that already happened, for the history of the seller. Importing it never moves an item nor
 * money.
 *
 * @param sourceId   stable identifier of the row in the source, see {@link ImportedAuction#sourceId()}.
 * @param sellerUuid the seller.
 * @param sellerName the seller's name, or {@code null} to let FAuction resolve it.
 * @param buyerUuid  the buyer.
 * @param buyerName  the buyer's name, or {@code null} to let FAuction resolve it.
 * @param item       the item sold.
 * @param price      the price it was sold at.
 * @param listedAt   when it was put on sale, {@code null} if the source does not keep it.
 * @param soldAt     when it was sold, {@code null} if the source does not keep it. Falls back on
 *                   {@code listedAt}, then on the date of the import.
 */
public record ImportedHistoric(String sourceId, UUID sellerUuid, @Nullable String sellerName,
                               UUID buyerUuid, @Nullable String buyerName, ImportedItem item, double price,
                               @Nullable Instant listedAt, @Nullable Instant soldAt) implements ImportedRow {

    @Override
    public ImportDataType type() {
        return ImportDataType.HISTORIC;
    }

    public ImportedHistoric {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(sellerUuid, "sellerUuid");
        Objects.requireNonNull(buyerUuid, "buyerUuid");
        Objects.requireNonNull(item, "item");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String sourceId;
        private UUID sellerUuid;
        private String sellerName;
        private UUID buyerUuid;
        private String buyerName;
        private ImportedItem item;
        private double price;
        private Instant listedAt;
        private Instant soldAt;

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

        public Builder buyer(UUID uuid, @Nullable String name) {
            this.buyerUuid = uuid;
            this.buyerName = name;
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

        public Builder listedAt(@Nullable Instant listedAt) {
            this.listedAt = listedAt;
            return this;
        }

        public Builder soldAt(@Nullable Instant soldAt) {
            this.soldAt = soldAt;
            return this;
        }

        /**
         * @throws NullPointerException if a mandatory field is missing.
         */
        public ImportedHistoric build() {
            return new ImportedHistoric(sourceId, sellerUuid, sellerName, buyerUuid, buyerName, item, price, listedAt, soldAt);
        }
    }
}
