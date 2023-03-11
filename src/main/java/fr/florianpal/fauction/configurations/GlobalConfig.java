
/*
 * Copyright (C) 2022 Florianpal
 *
 * This program is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses/>.
 *
 * Last modification : 07/01/2022 23:07
 *
 *  @author Florianpal.
 */

package fr.florianpal.fauction.configurations;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class GlobalConfig {

    private String lang = "en";
    private String orderBy;

    private Map<Material, Double> minPrice = new EnumMap<>(Material.class);

    private boolean auctionOnBuyCommandUse;
    private String auctionOnBuyMessage;
    private Map<String, Integer> auctionLimitations = new HashMap<>();
    private int auctionTime;
    private int auctionCheckEvery;

    private boolean billOnBuyCommandUse;
    private String billOnBuyMessage;
    private Map<String, Integer> billLimitations = new HashMap<>();
    private int billTime;
    private int billCheckEvery;

    public void load(Configuration config) {
        lang = config.getString("global.lang");
        orderBy = config.getString("global.orderBy");

        minPrice = new EnumMap<>(Material.class);
        for (String material : config.getConfigurationSection("global.min-price").getKeys(false)) {
            minPrice.put(Material.valueOf(material), config.getDouble("global.min-price." + material));
        }

        auctionOnBuyCommandUse = config.getBoolean("auction.onBuy.sendMessage.use");
        auctionOnBuyMessage = config.getString("auction.onBuy.sendMessage.command");
        auctionTime = config.getInt("auction.expiration.time");
        auctionCheckEvery = config.getInt("auction.expiration.checkEvery");

        auctionLimitations = new HashMap<>();
        for (String limitationGroup : config.getConfigurationSection("auction.limitations").getKeys(false)) {
            auctionLimitations.put(limitationGroup, config.getInt("auction.limitations." + limitationGroup));
        }

        billOnBuyCommandUse = config.getBoolean("bill.onBuy.sendMessage.use");
        billOnBuyMessage = config.getString("bill.onBuy.sendMessage.command");
        billTime = config.getInt("bill.expiration.time");
        billCheckEvery = config.getInt("bill.expiration.checkEvery");

        billLimitations = new HashMap<>();
        for (String limitationGroup : config.getConfigurationSection("bill.limitations").getKeys(false)) {
            billLimitations.put(limitationGroup, config.getInt("bill.limitations." + limitationGroup));
        }
    }

    public int getAuctionTime() {
        return auctionTime;
    }

    public int getAuctionCheckEvery() {
        return auctionCheckEvery;
    }

    public Map<String, Integer> getAuctionLimitations() {
        return auctionLimitations;
    }

    public boolean isAuctionOnBuyCommandUse() {
        return auctionOnBuyCommandUse;
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

    public boolean isBillOnBuyCommandUse() {
        return billOnBuyCommandUse;
    }

    public int getBillTime() {
        return billTime;
    }

    public int getBillCheckEvery() {
        return billCheckEvery;
    }

    public Map<String, Integer> getBillLimitations() {
        return billLimitations;
    }

    public String getBillOnBuyMessage() {
        return billOnBuyMessage;
    }

    public String getAuctionOnBuyMessage() {
        return auctionOnBuyMessage;
    }
}
