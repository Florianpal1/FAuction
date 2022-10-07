
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

package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigurationManager {
    private final DatabaseConfig database = new DatabaseConfig();
    private final FileConfiguration databaseConfig;

    private final MainGuiConfig mainGuiConfig = new MainGuiConfig();
    private final FileConfiguration mainGuiConfiguration;

    private final ExpireGuiConfig expireGuiConfig = new ExpireGuiConfig();
    private final FileConfiguration expireGuiConfiguration;

    private final ConfirmGuiConfig confirmConfig = new ConfirmGuiConfig();
    private final FileConfiguration confirmConfiguration;

    private final GlobalConfig globalConfig = new GlobalConfig();
    private final FileConfiguration globalConfiguration;

    public ConfigurationManager(FAuction core) {

        File databaseFile = new File(core.getDataFolder(), "database.yml");
        core.createDefaultConfiguration(databaseFile, "database.yml");
        databaseConfig = YamlConfiguration.loadConfiguration(databaseFile);

        File mainGuiFile = new File(core.getDataFolder(), "mainGui.yml");
        core.createDefaultConfiguration(mainGuiFile, "mainGui.yml");
        mainGuiConfiguration = YamlConfiguration.loadConfiguration(mainGuiFile);

        File expireGuiFile = new File(core.getDataFolder(), "expireGui.yml");
        core.createDefaultConfiguration(expireGuiFile, "expireGui.yml");
        expireGuiConfiguration = YamlConfiguration.loadConfiguration(expireGuiFile);

        File confirmFile = new File(core.getDataFolder(), "confirmGui.yml");
        core.createDefaultConfiguration(confirmFile, "confirmGui.yml");
        confirmConfiguration = YamlConfiguration.loadConfiguration(confirmFile);

        File globalFile = new File(core.getDataFolder(), "config.yml");
        core.createDefaultConfiguration(globalFile, "config.yml");
        globalConfiguration = YamlConfiguration.loadConfiguration(globalFile);

        globalConfig.load(globalConfiguration);
        mainGuiConfig.load(mainGuiConfiguration);
        confirmConfig.load(confirmConfiguration);
        expireGuiConfig.load(expireGuiConfiguration);
        database.load(databaseConfig);
    }

    public void reload() {
        globalConfig.load(globalConfiguration);
        mainGuiConfig.load(mainGuiConfiguration);
        confirmConfig.load(confirmConfiguration);
        expireGuiConfig.load(expireGuiConfiguration);
        database.load(databaseConfig);
    }

    public DatabaseConfig getDatabase() {
        return database;
    }

    public MainGuiConfig getMainGuiConfig() {
        return mainGuiConfig;
    }

    public GlobalConfig getGlobalConfig() {
        return globalConfig;
    }

    public ConfirmGuiConfig getConfirmConfig() {
        return confirmConfig;
    }

    public ExpireGuiConfig getExpireGuiConfig() {
        return expireGuiConfig;
    }
}
