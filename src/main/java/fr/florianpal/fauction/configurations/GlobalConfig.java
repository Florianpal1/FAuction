package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.objects.SpamLimit;
import lombok.Getter;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
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

    private long spamMessageCooldown = 3000;

    private int spamLogThreshold = 25;

    private final Map<SpamAction, SpamLimit> spamLimits = new EnumMap<>(SpamAction.class);

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

    private int bidTime;
    private int bidCheckEvery;
    private double bidMinIncrement;
    private boolean bidApplyDefaultPriceLimits;
    private Map<String, Integer> bidLimitations = new HashMap<>();
    private boolean bidFeatureFlippingExpiration;

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
        spamMessageCooldown = config.getLong("anti-spam.messageCooldown", 3000L);
        spamLogThreshold = config.getInt("anti-spam.logThreshold", 25);

        spamLimits.clear();
        for (SpamAction action : SpamAction.values()) {
            SpamLimit defaultLimit = action.getDefaultLimit();
            double burst = config.getDouble("anti-spam." + action.getConfigKey() + ".burst", defaultLimit.burst());
            double perSecond = config.getDouble("anti-spam." + action.getConfigKey() + ".perSecond", defaultLimit.perSecond());
            spamLimits.put(action, new SpamLimit(Math.max(1, burst), Math.max(0.1, perSecond)));
        }

        // A period of zero makes Bukkit schedule the task every tick, so a missing section has to
        // fall back on a usable value.
        time = config.getInt("expiration.time", 3600);
        checkEvery = config.getInt("expiration.checkEvery", 72000);
        updateCacheEvery = config.getInt("cacheUpdate", 72000);

        timeCurrency = config.getInt("currencyCheck.time", 3600);
        checkEveryCurrency = config.getInt("currencyCheck.checkEvery", 72000);

        bidFeatureFlippingExpiration = config.getBoolean("bid.feature-flipping.item-expiration", true);
        bidTime = config.getInt("bid.expiration.time", 3600);
        bidCheckEvery = config.getInt("bid.expiration.checkEvery", 72000);
        // A zero or negative increment would let a bid be raised for free (or below the previous
        // bid, creating money out of nothing on the refund), so it is floored the same way the
        // anti-spam limits are above.
        bidMinIncrement = Math.max(0.01, config.getDouble("bid.minIncrement", 1.0));
        bidApplyDefaultPriceLimits = config.getBoolean("bid.applyDefaultPriceLimits", false);

        bidLimitations = new HashMap<>();
        if (config.contains("bid.limitations")) {
            for (Object limitationGroup : config.getSection("bid.limitations").getKeys()) {
                bidLimitations.put(limitationGroup.toString(), config.getInt("bid.limitations." + limitationGroup));
            }
        }

        minPrice = new HashMap<>();
        maxPrice = new HashMap<>();
        blacklistItem = new ArrayList<>();

        limitationsUseMetaLuckperms = config.getBoolean("limitations-use-meta-luckperms", false);
        limitations = new HashMap<>();
        if (config.contains("limitations")) {
            for (Object limitationGroup : config.getSection("limitations").getKeys()) {
                limitations.put(limitationGroup.toString(), config.getInt("limitations." + limitationGroup));
            }
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

    public SpamLimit getSpamLimit(SpamAction action) {
        return spamLimits.getOrDefault(action, action.getDefaultLimit());
    }

}
