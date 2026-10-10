package fr.florianpal.fauction.api.importer;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of data an import module can provide.
 */
public enum ImportDataType {

    /**
     * An item currently for sale, {@link ImportedAuction}.
     */
    AUCTION,

    /**
     * An item waiting to be collected by its owner (expired or withdrawn sale), {@link ImportedExpired}.
     */
    EXPIRED,

    /**
     * A sale that already happened, {@link ImportedHistoric}.
     */
    HISTORIC,

    /**
     * Money owed to a player and not paid yet, {@link ImportedPendingCurrency}.
     */
    PENDING_CURRENCY;

    /**
     * The name used on the command line ({@code --types auction,expired}) : the constant in lower
     * case, {@code pending_currency} also accepting {@code pending}.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ImportDataType> byId(String id) {
        String normalized = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("pending".equals(normalized)) {
            return Optional.of(PENDING_CURRENCY);
        }
        return Arrays.stream(values()).filter(type -> type.id().equals(normalized)).findFirst();
    }
}
