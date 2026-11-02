package fr.florianpal.fauction.api.importer;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Money owed to a player by the source plugin and not paid yet (sale not collected, bid to refund...).
 * FAuction pays it the next time the player is online.
 *
 * @param sourceId       stable identifier of the row in the source, see {@link ImportedAuction#sourceId()}.
 * @param playerUuid     the player to pay.
 * @param sourceCurrency the name of the currency on the source side ({@code "Vault"}...). FAuction
 *                       converts it through {@code import.currency-map} of its config.yml ; a currency
 *                       that is not mapped rejects the row, it is never converted at random.
 * @param amount         the amount, strictly positive.
 * @param reason         why the money is owed, shown in the report ; may be {@code null}.
 */
public record ImportedPendingCurrency(String sourceId, UUID playerUuid, String sourceCurrency, double amount,
                                      @Nullable String reason) implements ImportedRow {

    @Override
    public ImportDataType type() {
        return ImportDataType.PENDING_CURRENCY;
    }

    public ImportedPendingCurrency {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(sourceCurrency, "sourceCurrency");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String sourceId;
        private UUID playerUuid;
        private String sourceCurrency;
        private double amount;
        private String reason;

        private Builder() {
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder player(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder currency(String sourceCurrency) {
            this.sourceCurrency = sourceCurrency;
            return this;
        }

        public Builder amount(double amount) {
            this.amount = amount;
            return this;
        }

        public Builder reason(@Nullable String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * @throws NullPointerException if a mandatory field is missing.
         */
        public ImportedPendingCurrency build() {
            return new ImportedPendingCurrency(sourceId, playerUuid, sourceCurrency, amount, reason);
        }
    }
}
