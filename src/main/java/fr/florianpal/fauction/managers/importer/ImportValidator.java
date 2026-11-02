package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedItem;
import fr.florianpal.fauction.api.importer.ImportedPendingCurrency;
import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.queries.CurrencyPendingRow;
import fr.florianpal.fauction.queries.HistoricRow;
import fr.florianpal.fauction.queries.ImportLogQueries;
import fr.florianpal.fauction.queries.ItemRow;
import org.bukkit.inventory.ItemStack;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The validation of FAuction, never delegated to a module : every row pushed goes through here before
 * it can reach the database.
 */
public class ImportValidator {

    /**
     * {@code playerName} is a {@code VARCHAR(36)} in every table.
     */
    static final int MAX_NAME_LENGTH = 36;

    /**
     * The encoded item as handed by the module, checked before any decoding : a crafted file must not
     * make the server decompress or deserialize an arbitrary amount of data.
     */
    static final int MAX_SOURCE_ITEM_BYTES = 1024 * 1024;

    static final int MAX_STORED_ITEM_BYTES = 65_535;

    /**
     * The largest stack the game lets an item have.
     */
    static final int MAX_STACK_AMOUNT = 99;

    /**
     * Section sign codes, hex included ({@code §x§r§r§g§g§b§b}), and the {@code &} codes and
     * {@code &#RRGGBB} an administrator types.
     */
    private static final Pattern COLOR_CODES = Pattern.compile("(?i)(§[0-9a-fk-orx]|&#[0-9a-f]{6}|&[0-9a-fk-or])");

    /**
     * The outcome of the validation of one row : a row to write, or the reason it is refused.
     */
    public record Result(PreparedRow row, String rejection) {

        static Result ok(PreparedRow row) {
            return new Result(row, null);
        }

        static Result rejected(String reason) {
            return new Result(null, reason);
        }

        public boolean isRejected() {
            return rejection != null;
        }
    }

    private final ImportSettings settings;

    private final ImportOptions options;

    private final ItemCodec codec;

    private final Function<UUID, String> nameLookup;

    private final Clock clock;

    private final Map<UUID, String> resolvedNames = new ConcurrentHashMap<>();

    /**
     * @param nameLookup the name the server knows for a UUID, {@code null} when it knows none.
     */
    public ImportValidator(ImportSettings settings, ImportOptions options, ItemCodec codec,
                           Function<UUID, String> nameLookup, Clock clock) {
        this.settings = settings;
        this.options = options;
        this.codec = codec;
        this.nameLookup = nameLookup;
        this.clock = clock;
    }

    public Result validate(ImportedAuction auction) {
        Optional<String> idError = checkSourceId(auction.sourceId());
        if (idError.isPresent()) {
            return Result.rejected(idError.get());
        }
        if (!isPositivePrice(auction.price())) {
            return Result.rejected("Invalid price " + auction.price() + " : it must be a finite number above 0");
        }

        ItemStack item;
        byte[] bytes;
        try {
            item = decode(auction.item());
            bytes = checkStoredSize(codec.encode(item));
        } catch (InvalidRowException e) {
            return Result.rejected(e.getMessage());
        } catch (Exception e) {
            return Result.rejected("Item cannot be stored : " + describe(e));
        }

        Instant now = clock.instant();
        Instant listedAt = notInFuture(auction.listedAt(), now);
        ItemRow row = new ItemRow(auction.sellerUuid(), name(auction.sellerUuid(), auction.sellerName()), bytes,
                auction.price(), listedAt.toEpochMilli());

        // The sale cannot be put on sale as is : given back to its seller instead, or refused.
        String moveReason = null;

        if (auction.currency() != null) {
            Optional<CurrencyType> currency = settings.mapCurrency(auction.currency());
            if (currency.isEmpty() || currency.get() != settings.mainCurrency()) {
                String reason = "Sale in currency " + auction.currency()
                        + (currency.map(type -> " (" + type + ")").orElse(" (not in import.currency-map)"))
                        + ", FAuction sells in " + settings.mainCurrency();
                if (options.otherCurrency() == ImportOptions.OtherCurrency.REJECT) {
                    return Result.rejected(reason + " (--other-currency=expire gives the item back to the seller)");
                }
                moveReason = reason;
            }
        }

        if (moveReason == null && options.applyLimits()) {
            moveReason = settings.limits().check(item, auction.price()).orElse(null);
        }

        if (moveReason == null && options.expiredToExpires() && settings.expirationSeconds() >= 0
                && !listedAt.plusSeconds(settings.expirationSeconds()).isAfter(now)) {
            moveReason = "Already expired";
        }

        if (moveReason != null) {
            return Result.ok(new PreparedRow(ImportDataType.AUCTION, auction.sourceId(), PreparedRow.Target.EXPIRES, row, true, moveReason));
        }
        return Result.ok(new PreparedRow(ImportDataType.AUCTION, auction.sourceId(), PreparedRow.Target.AUCTIONS, row, false, null));
    }

    public Result validate(ImportedExpired expired) {
        Optional<String> idError = checkSourceId(expired.sourceId());
        if (idError.isPresent()) {
            return Result.rejected(idError.get());
        }
        // An item waiting to be collected may have no price : it is only displayed.
        if (!Double.isFinite(expired.price()) || expired.price() < 0) {
            return Result.rejected("Invalid price " + expired.price() + " : it must be a finite number, 0 or more");
        }

        byte[] bytes;
        try {
            bytes = checkStoredSize(codec.encode(decode(expired.item())));
        } catch (InvalidRowException e) {
            return Result.rejected(e.getMessage());
        } catch (Exception e) {
            return Result.rejected("Item cannot be stored : " + describe(e));
        }

        Instant listedAt = notInFuture(expired.listedAt(), clock.instant());
        ItemRow row = new ItemRow(expired.ownerUuid(), name(expired.ownerUuid(), expired.ownerName()), bytes,
                expired.price(), listedAt.toEpochMilli());
        return Result.ok(new PreparedRow(ImportDataType.EXPIRED, expired.sourceId(), PreparedRow.Target.EXPIRES, row, false, null));
    }

    public Result validate(ImportedHistoric historic) {
        Optional<String> idError = checkSourceId(historic.sourceId());
        if (idError.isPresent()) {
            return Result.rejected(idError.get());
        }
        if (!isPositivePrice(historic.price())) {
            return Result.rejected("Invalid price " + historic.price() + " : it must be a finite number above 0");
        }

        byte[] bytes;
        try {
            bytes = checkStoredSize(codec.encode(decode(historic.item())));
        } catch (InvalidRowException e) {
            return Result.rejected(e.getMessage());
        } catch (Exception e) {
            return Result.rejected("Item cannot be stored : " + describe(e));
        }

        Instant now = clock.instant();
        Instant listedAt = historic.listedAt() != null ? historic.listedAt() : historic.soldAt() != null ? historic.soldAt() : now;
        Instant soldAt = historic.soldAt() != null ? historic.soldAt() : historic.listedAt() != null ? historic.listedAt() : now;

        HistoricRow row = new HistoricRow(
                historic.sellerUuid(), name(historic.sellerUuid(), historic.sellerName()),
                historic.buyerUuid(), name(historic.buyerUuid(), historic.buyerName()),
                bytes, historic.price(),
                notInFuture(listedAt, now).toEpochMilli(), notInFuture(soldAt, now).toEpochMilli());
        return Result.ok(new PreparedRow(ImportDataType.HISTORIC, historic.sourceId(), PreparedRow.Target.HISTORIC, row, false, null));
    }

    public Result validate(ImportedPendingCurrency pending) {
        Optional<String> idError = checkSourceId(pending.sourceId());
        if (idError.isPresent()) {
            return Result.rejected(idError.get());
        }
        if (!isPositivePrice(pending.amount())) {
            return Result.rejected("Invalid amount " + pending.amount() + " : it must be a finite number above 0");
        }

        Optional<CurrencyType> currency = settings.mapCurrency(pending.sourceCurrency());
        if (currency.isEmpty()) {
            return Result.rejected("Currency " + pending.sourceCurrency() + " is not in import.currency-map of config.yml ("
                    + pending.amount() + " owed to " + pending.playerUuid() + ")");
        }

        CurrencyPendingRow row = new CurrencyPendingRow(pending.playerUuid(), currency.get(), pending.amount());
        return Result.ok(new PreparedRow(ImportDataType.PENDING_CURRENCY, pending.sourceId(), PreparedRow.Target.CURRENCY_PENDING, row, false, null));
    }

    private Optional<String> checkSourceId(String sourceId) {
        if (sourceId.isBlank()) {
            return Optional.of("Empty sourceId");
        }
        if (sourceId.length() > ImportLogQueries.MAX_SOURCE_ID_LENGTH) {
            return Optional.of("sourceId longer than " + ImportLogQueries.MAX_SOURCE_ID_LENGTH + " characters");
        }
        return Optional.empty();
    }

    private static boolean isPositivePrice(double price) {
        return Double.isFinite(price) && price > 0;
    }

    private ItemStack decode(ImportedItem imported) throws InvalidRowException {
        int size = imported.data().length;
        if (size > MAX_SOURCE_ITEM_BYTES) {
            throw new InvalidRowException("Item data too large (" + size + " bytes, " + MAX_SOURCE_ITEM_BYTES + " max)");
        }
        ItemStack item;
        try {
            item = codec.decode(imported);
        } catch (Exception | LinkageError e) {
            throw new InvalidRowException("Item cannot be decoded : " + describe(e));
        }
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            throw new InvalidRowException("Item is air or empty");
        }
        if (item.getAmount() > MAX_STACK_AMOUNT) {
            throw new InvalidRowException("Stack of " + item.getAmount() + " items, " + MAX_STACK_AMOUNT + " max");
        }
        return item;
    }

    /**
     * {@code item} is a {@code BLOB} : 65 535 bytes on MySQL/MariaDB. Refused here rather than in the
     * batch, where it would refuse its whole batch (strict mode) or be truncated silently.
     */
    private static byte[] checkStoredSize(byte[] bytes) throws InvalidRowException {
        if (bytes.length > MAX_STORED_ITEM_BYTES) {
            throw new InvalidRowException("Item too large for the database (" + bytes.length + " bytes, " + MAX_STORED_ITEM_BYTES + " max)");
        }
        return bytes;
    }

    private static Instant notInFuture(Instant date, Instant now) {
        return date.isAfter(now) ? now : date;
    }

    /**
     * The name to store : colour codes removed, cut to the size of the column, resolved from the UUID
     * when the source gave none (or nothing but colour codes), the UUID itself as a last resort.
     */
    String name(UUID uuid, String sourceName) {
        String cleaned = clean(sourceName);
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        return resolvedNames.computeIfAbsent(uuid, key -> {
            String resolved;
            try {
                resolved = clean(nameLookup.apply(key));
            } catch (RuntimeException e) {
                resolved = "";
            }
            return resolved.isEmpty() ? key.toString() : resolved;
        });
    }

    static String clean(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = COLOR_CODES.matcher(name).replaceAll("").trim();
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static final class InvalidRowException extends Exception {
        InvalidRowException(String message) {
            super(message);
        }
    }
}
