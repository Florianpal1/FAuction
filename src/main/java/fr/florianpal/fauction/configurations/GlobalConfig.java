package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.enums.CurrencyType;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GlobalConfig {

    private String lang = "en";
    private String orderBy;
    private boolean onBuyCommandUse;

    private boolean securityForSpammingPacket;

    private String dateFormat;

    private String remainingDateFormat;

    private String onBuyCommand;

    private boolean limitationsUseMetaLuckperms = false;
    private Map<String, Integer> limitations = new HashMap<>();
    private Map<Material, Double> minPrice = new HashMap<>();

    private Map<Material, Double> maxPrice = new HashMap<>();

    private List<Material> blacklistItem = new ArrayList<>();

    private boolean defaultMaxValueEnable = false;

    private boolean defaultMinValueEnable = false;

    private double defaultMinValue = 0;

    private double defaultMaxValue = 100000000;

    private int time;
    private int checkEvery;

    private int timeCurrency;
    private int checkEveryCurrency;

    private int updateCacheEvery;

    private boolean featureFlippingExpiration;

    private boolean featureFlippingCacheUpdate;

    private boolean featureFlippingMoneyFormat;

    private boolean featureDuplicationHashCodeControl;

    private String decimalFormat;

    private String defaultGui;

    private CurrencyType currencyType;

    public void load(YamlDocument config) {
        lang = config.getString("lang");

        defaultGui = config.getString("defaultGui", "AUCTION");

        currencyType = CurrencyType.valueOf(config.getString("currencyUse", "VAULT"));

        decimalFormat = config.getString("decimalFormat", "0.00");

        featureFlippingExpiration = config.getBoolean("feature-flipping.item-expiration", true);
        featureFlippingCacheUpdate = config.getBoolean("feature-flipping.cache-update", true);
        featureFlippingMoneyFormat = config.getBoolean("feature-flipping.money-format", false);
        featureDuplicationHashCodeControl = config.getBoolean("feature-flipping.duplication-hashcode-control", false);

        orderBy = config.getString("orderBy");
        dateFormat = config.getString("dateFormat");
        remainingDateFormat = config.getString("remainingDateFormat");
        onBuyCommandUse = config.getBoolean("onBuy.sendCommand.use");
        onBuyCommand = config.getString("onBuy.sendCommand.command");

        securityForSpammingPacket = config.getBoolean("securityForSpammingPacket", true);

        time = config.getInt("expiration.time");
        checkEvery = config.getInt("expiration.checkEvery");
        updateCacheEvery = config.getInt("cacheUpdate", 72000);

        timeCurrency = config.getInt("currency.time");
        checkEveryCurrency = config.getInt("currency.checkEvery");

        minPrice = new HashMap<>();
        maxPrice = new HashMap<>();
        blacklistItem = new ArrayList<>();

        limitationsUseMetaLuckperms = config.getBoolean("limitations-use-meta-luckperms", false);
        limitations = new HashMap<>();
        for (Object limitationGroup : config.getSection("limitations").getKeys()) {
            limitations.put(limitationGroup.toString(), config.getInt("limitations." + limitationGroup));
        }

        if (config.contains("min-price-default")) {
            defaultMinValueEnable = config.getBoolean("min-price-default.enable");
            defaultMinValue = config.getDouble("min-price-default.value");
        }

        if (config.contains("max-price-default")) {
            defaultMaxValueEnable = config.getBoolean("max-price-default.enable");
            defaultMaxValue = config.getDouble("max-price-default.value");
        }

        if (config.contains("min-price")) {
            minPrice = new HashMap<>();
            for (Object material : config.getSection("min-price").getKeys()) {
                minPrice.put(Material.valueOf(material.toString()), config.getDouble("min-price." + material));
            }
        }

        if (config.contains("max-price")) {
            maxPrice = new HashMap<>();
            for (Object material : config.getSection("max-price").getKeys()) {
                maxPrice.put(Material.valueOf(material.toString()), config.getDouble("max-price." + material));
            }
        }

        if (config.contains("item-blacklist")) {
            blacklistItem = config.getStringList("item-blacklist").stream().map(Material::valueOf).collect(Collectors.toList());
        }
    }

    public int getTime() {
        return time;
    }

    public int getCheckEvery() {
        return checkEvery;
    }

    public Map<String, Integer> getLimitations() {
        return limitations;
    }

    public boolean isOnBuyCommandUse() {
        return onBuyCommandUse;
    }

    public String getOnBuyCommand() {
        return onBuyCommand;
    }

    public Map<Material, Double> getMinPrice() {
        return minPrice;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public String getLang() {
        return lang;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public boolean isSecurityForSpammingPacket() {
        return securityForSpammingPacket;
    }

    public Map<Material, Double> getMaxPrice() {
        return maxPrice;
    }

    public boolean isDefaultMaxValueEnable() {
        return defaultMaxValueEnable;
    }

    public boolean isDefaultMinValueEnable() {
        return defaultMinValueEnable;
    }

    public double getDefaultMinValue() {
        return defaultMinValue;
    }

    public double getDefaultMaxValue() {
        return defaultMaxValue;
    }

    public List<Material> getBlacklistItem() {
        return blacklistItem;
    }

    public boolean isLimitationsUseMetaLuckperms() {
        return limitationsUseMetaLuckperms;
    }

    public int getUpdateCacheEvery() {
        return updateCacheEvery;
    }

    public String getRemainingDateFormat() {
        return remainingDateFormat;
    }

    public boolean isFeatureFlippingExpiration() {
        return featureFlippingExpiration;
    }

    public boolean isFeatureFlippingCacheUpdate() {
        return featureFlippingCacheUpdate;
    }

    public String getDecimalFormat() {
        return decimalFormat;
    }

    public String getDefaultGui() {
        return defaultGui;
    }

    public CurrencyType getCurrencyType() {
        return currencyType;
    }

    public int getTimeCurrency() {
        return timeCurrency;
    }

    public int getCheckEveryCurrency() {
        return checkEveryCurrency;
    }

    public boolean isFeatureFlippingMoneyFormat() {
        return featureFlippingMoneyFormat;
    }

    public boolean isFeatureDuplicationHashCodeControl() {
        return featureDuplicationHashCodeControl;
    }
}
