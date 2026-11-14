package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The options of {@code /ah admin import run <id> [options]}.
 *
 * @param types            the requested types, empty for every type the module supports.
 * @param dryRun           validate and report, write nothing.
 * @param applyLimits      a sale breaking the price limits or the blacklist of config.yml is given back
 *                         to its seller instead of being put on sale.
 * @param expiredToExpires a sale already expired by {@code expiration.time} goes straight to the
 *                         expired items of its seller.
 * @param forceReimport    ignore the journal of the previous imports.
 * @param otherCurrency    what to do with a sale in another currency than the one of FAuction.
 * @param convertBids      convert the auctions with bids of the source, or skip them.
 * @param moduleOptions    the {@code --opt key=value} of the module, keys in lower case.
 * @param confirm          the second typing that confirms a real import.
 */
public record ImportOptions(Set<ImportDataType> types, boolean dryRun, boolean applyLimits, boolean expiredToExpires,
                            boolean forceReimport, OtherCurrency otherCurrency, boolean convertBids,
                            Map<String, String> moduleOptions, boolean confirm) {

    public enum OtherCurrency {
        REJECT,
        EXPIRE
    }

    public static final ImportOptions DEFAULTS = new ImportOptions(Set.of(), false, false, false, false,
            OtherCurrency.REJECT, true, Map.of(), false);

    public ImportOptions {
        types = types.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(types));
        moduleOptions = Collections.unmodifiableMap(new LinkedHashMap<>(moduleOptions));
    }

    /**
     * The same options, confirmation aside : what has to match between the two typings.
     */
    public ImportOptions withoutConfirm() {
        return new ImportOptions(types, dryRun, applyLimits, expiredToExpires, forceReimport, otherCurrency, convertBids, moduleOptions, false);
    }

    /**
     * Reads the options typed after the id. Accepts {@code --name value} as well as
     * {@code --name=value}.
     *
     * @throws IllegalArgumentException naming the faulty option.
     */
    public static ImportOptions parse(String input) {
        Set<ImportDataType> types = EnumSet.noneOf(ImportDataType.class);
        boolean dryRun = false;
        boolean applyLimits = false;
        boolean expiredToExpires = false;
        boolean forceReimport = false;
        OtherCurrency otherCurrency = OtherCurrency.REJECT;
        boolean convertBids = true;
        Map<String, String> moduleOptions = new LinkedHashMap<>();
        boolean confirm = false;

        List<String> tokens = new ArrayList<>();
        if (input != null) {
            for (String token : input.trim().split("\\s+")) {
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        }

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if ("confirm".equalsIgnoreCase(token)) {
                confirm = true;
                continue;
            }
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected \"" + token + "\" : options start with --");
            }

            String name = token.substring(2).toLowerCase(Locale.ROOT);
            String value = null;
            int equals = name.indexOf('=');
            if (equals >= 0) {
                value = token.substring(2 + equals + 1);
                name = name.substring(0, equals);
            }

            switch (name) {
                case "dry-run" -> dryRun = flag(name, value);
                case "apply-limits" -> applyLimits = flag(name, value);
                case "expired-to-expires" -> expiredToExpires = flag(name, value);
                case "force-reimport" -> forceReimport = flag(name, value);
                case "types", "other-currency", "bids", "opt" -> {
                    if (value == null) {
                        if (i + 1 >= tokens.size() || tokens.get(i + 1).startsWith("--")) {
                            throw new IllegalArgumentException("--" + name + " needs a value");
                        }
                        value = tokens.get(++i);
                    }
                    switch (name) {
                        case "types" -> {
                            for (String type : value.split(",")) {
                                if (type.isBlank()) {
                                    continue;
                                }
                                types.add(ImportDataType.byId(type).orElseThrow(() -> new IllegalArgumentException(
                                        "Unknown type \"" + type + "\" : auction, expired, historic, pending_currency")));
                            }
                            if (types.isEmpty()) {
                                throw new IllegalArgumentException("--types needs at least one type");
                            }
                        }
                        case "other-currency" -> otherCurrency = switch (value.toLowerCase(Locale.ROOT)) {
                            case "reject" -> OtherCurrency.REJECT;
                            case "expire" -> OtherCurrency.EXPIRE;
                            default -> throw new IllegalArgumentException("--other-currency is reject or expire, not \"" + value + "\"");
                        };
                        case "bids" -> convertBids = switch (value.toLowerCase(Locale.ROOT)) {
                            case "convert" -> true;
                            case "skip" -> false;
                            default -> throw new IllegalArgumentException("--bids is convert or skip, not \"" + value + "\"");
                        };
                        default -> {
                            int separator = value.indexOf('=');
                            if (separator <= 0) {
                                throw new IllegalArgumentException("--opt is key=value, not \"" + value + "\"");
                            }
                            moduleOptions.put(value.substring(0, separator).toLowerCase(Locale.ROOT), value.substring(separator + 1));
                        }
                    }
                }
                default -> throw new IllegalArgumentException("Unknown option --" + name);
            }
        }

        return new ImportOptions(types, dryRun, applyLimits, expiredToExpires, forceReimport, otherCurrency, convertBids, moduleOptions, confirm);
    }

    private static boolean flag(String name, String value) {
        if (value != null) {
            throw new IllegalArgumentException("--" + name + " takes no value");
        }
        return true;
    }
}
