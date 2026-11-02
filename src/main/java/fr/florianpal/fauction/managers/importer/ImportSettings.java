package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.CurrencyType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the import reads from config.yml, taken once when an import starts : a {@code /ah admin reload}
 * during an import does not change its rules halfway.
 *
 * @param batchSize               rows written per transaction.
 * @param progressIntervalSeconds delay between two progress lines in the console, 0 to disable.
 * @param currencyMap             source currency, in lower case, to the currency of FAuction.
 * @param mainCurrency            {@code currencyUse} : the currency the sales of FAuction are paid in.
 * @param expirationSeconds       {@code expiration.time}, or -1 when the sales never expire.
 */
public record ImportSettings(int batchSize, int progressIntervalSeconds, Map<String, CurrencyType> currencyMap,
                             CurrencyType mainCurrency, long expirationSeconds, Limits limits) {

    /**
     * The price limits and the blacklist of config.yml, for {@code --apply-limits}.
     */
    public record Limits(Set<Material> blacklist, Map<Material, Double> minPrice, Map<Material, Double> maxPrice,
                         boolean defaultMinEnabled, double defaultMin, boolean defaultMaxEnabled, double defaultMax) {

        public static final Limits NONE = new Limits(Set.of(), Map.of(), Map.of(), false, 0, false, 0);

        public Limits {
            blacklist = Set.copyOf(blacklist);
            minPrice = Map.copyOf(minPrice);
            maxPrice = Map.copyOf(maxPrice);
        }

        /**
         * The same rules as {@code /ah sell}, contents of a shulker box aside.
         *
         * @return why the sale breaks the limits, empty if it does not.
         */
        public Optional<String> check(ItemStack item, double price) {
            Material type = item.getType();
            if (blacklist.contains(type)) {
                return Optional.of("Blacklisted item " + type);
            }

            Double unitMin = minPrice.containsKey(type) ? minPrice.get(type) : defaultMinEnabled ? defaultMin : null;
            if (unitMin != null && unitMin * item.getAmount() > price) {
                return Optional.of("Price " + price + " under the minimum " + unitMin * item.getAmount());
            }

            Double unitMax = maxPrice.containsKey(type) ? maxPrice.get(type) : defaultMaxEnabled ? defaultMax : null;
            if (unitMax != null && unitMax * item.getAmount() < price) {
                return Optional.of("Price " + price + " over the maximum " + unitMax * item.getAmount());
            }
            return Optional.empty();
        }
    }

    public ImportSettings {
        if (batchSize < 1) {
            batchSize = 500;
        }
        progressIntervalSeconds = Math.max(0, progressIntervalSeconds);
        currencyMap = Map.copyOf(currencyMap);
    }

    public Optional<CurrencyType> mapCurrency(String sourceCurrency) {
        if (sourceCurrency == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(currencyMap.get(sourceCurrency.trim().toLowerCase(Locale.ROOT)));
    }

    public static ImportSettings from(GlobalConfig config) {
        return new ImportSettings(
                config.getImportBatchSize(),
                config.getImportProgressIntervalSeconds(),
                config.getImportCurrencyMap(),
                config.getCurrencyType(),
                config.isFeatureFlippingExpiration() ? config.getTime() : -1,
                new Limits(Set.copyOf(config.getBlacklistItem()), config.getMinPrice(), config.getMaxPrice(),
                        config.isDefaultMinValueEnable(), config.getDefaultMinValue(),
                        config.isDefaultMaxValueEnable(), config.getDefaultMaxValue()));
    }
}
