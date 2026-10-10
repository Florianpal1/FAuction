package fr.florianpal.fauction.api.importer;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An item waiting to be collected by its owner : expired or withdrawn sale, item won or bought and
 * not delivered yet... It lands in the owner's {@code /ah expire}.
 *
 * @param sourceId  stable identifier of the row in the source, see {@link ImportedAuction#sourceId()}.
 * @param ownerUuid the player who will collect the item.
 * @param ownerName the owner's name, or {@code null} to let FAuction resolve it.
 * @param item      the item.
 * @param price     the price it was listed at, for display only ; {@code 0} when there is none.
 * @param listedAt  the date shown with the item (listing date, or the date of the import when the
 *                  source keeps none).
 */
public record ImportedExpired(String sourceId, UUID ownerUuid, @Nullable String ownerName, ImportedItem item,
                              double price, Instant listedAt) implements ImportedRow {

    @Override
    public ImportDataType type() {
        return ImportDataType.EXPIRED;
    }

    public ImportedExpired {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(listedAt, "listedAt");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String sourceId;
        private UUID ownerUuid;
        private String ownerName;
        private ImportedItem item;
        private double price;
        private Instant listedAt;

        private Builder() {
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder owner(UUID uuid, @Nullable String name) {
            this.ownerUuid = uuid;
            this.ownerName = name;
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

        /**
         * @throws NullPointerException if a mandatory field is missing.
         */
        public ImportedExpired build() {
            return new ImportedExpired(sourceId, ownerUuid, ownerName, item, price, listedAt);
        }
    }
}
