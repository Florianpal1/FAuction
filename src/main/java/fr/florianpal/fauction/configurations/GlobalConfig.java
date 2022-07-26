
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

import java.util.HashMap;
import java.util.Map;

public class GlobalConfig {

    private String lang;
    private String orderBy;
    private boolean onBuyCommandUse;
    private String onBuyCommand;
    private Map<String, Integer> limitations = new HashMap<>();
    private Map<Material, Double> minPrice = new HashMap<>();
    private int time;
    private int checkEvery;

    public void load(Configuration config) {
        lang = config.getString("lang");
        orderBy = config.getString("orderBy");
        onBuyCommandUse = config.getBoolean("onBuy.sendCommand.use");
        onBuyCommand = config.getString("onBuy.sendCommand.command");
        time = config.getInt("expiration.time");
        checkEvery = config.getInt("expiration.checkEvery");

        limitations = new HashMap<>();
        for (String limitationGroup : config.getConfigurationSection("limitations").getKeys(false)) {
            limitations.put(limitationGroup, config.getInt("limitations." + limitationGroup));
        }

        minPrice = new HashMap<>();
        for (String material : config.getConfigurationSection("min-price").getKeys(false)) {
            minPrice.put(Material.valueOf(material), config.getDouble("min-price." + material));
        }
    }

    public void save(Configuration config) {}

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
}
