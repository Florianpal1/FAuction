package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.enums.CurrencyType;
import lombok.Getter;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
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

}
