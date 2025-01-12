package fr.florianpal.fauction.managers;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.CategoriesConfig;
import fr.florianpal.fauction.configurations.DatabaseConfig;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.configurations.gui.*;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class ConfigurationManager {
    private final DatabaseConfig database = new DatabaseConfig();
    private final YamlDocument databaseConfig;

    private final AuctionConfig auctionConfig = new AuctionConfig();
    private YamlDocument auctionConfiguration;

    private final HistoricConfig historicConfig = new HistoricConfig();
    private YamlDocument historicConfiguration;

    private final PlayerViewConfig playerViewConfig = new PlayerViewConfig();
    private YamlDocument playerViewConfiguration;

    private final ExpireGuiConfig expireConfig = new ExpireGuiConfig();
    private YamlDocument expireConfiguration;

    private final AuctionConfirmGuiConfig auctionConfirmConfig = new AuctionConfirmGuiConfig();
    private YamlDocument auctionConfirmConfiguration;

    private final GlobalConfig globalConfig = new GlobalConfig();
    private YamlDocument globalConfiguration;


    private final CategoriesConfig categoriesConfig = new CategoriesConfig();
    private YamlDocument categoriesConfiguration;

    public ConfigurationManager(FAuction plugin, File pluginFile) {

        try {
            databaseConfig = YamlDocument.create(new File(plugin.getDataFolder(), "database.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/database.yml")),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );
        } catch (IOException e) {
            plugin.getLogger().severe("Error in database configuration load : " + e.getMessage());
            throw new RuntimeException(e);
        }
        database.load(databaseConfig);
        loadAllConfiguration(plugin);
    }

    public void reload(FAuction plugin) {
        loadAllConfiguration(plugin);
    }

    private void loadAllConfiguration(FAuction plugin) {

        try {

        auctionConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/auction.yml"),
                Objects.requireNonNull(getClass().getResourceAsStream("/gui/auction.yml")),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(false).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
        );


        historicConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/historic.yml"),
                Objects.requireNonNull(getClass().getResourceAsStream("/gui/historic.yml")),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(false).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
        );

        playerViewConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/playerView.yml"),
                Objects.requireNonNull(getClass().getResourceAsStream("/gui/playerView.yml")),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(false).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
        );

        expireConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/expire.yml"),
                Objects.requireNonNull(getClass().getResourceAsStream("/gui/expire.yml")),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(false).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
        );

            auctionConfirmConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/auctionConfirm.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/gui/auctionConfirm.yml")),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(false).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            globalConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "config.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            categoriesConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "categories.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/categories.yml")),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );
        } catch (IOException e) {
            plugin.getLogger().severe("Error in configuration load : " + e.getMessage());
            throw new RuntimeException(e);
        }

        globalConfig.load(globalConfiguration);
        categoriesConfig.load(categoriesConfiguration);
        auctionConfig.load(plugin, auctionConfiguration);
        historicConfig.load(plugin, historicConfiguration);
        auctionConfirmConfig.load(plugin, auctionConfirmConfiguration);
        expireConfig.load(plugin, expireConfiguration);
        playerViewConfig.load(plugin, playerViewConfiguration);
    }

    public DatabaseConfig getDatabase() {
        return database;
    }

    public AuctionConfig getAuctionConfig() {
        return auctionConfig;
    }

    public GlobalConfig getGlobalConfig() {
        return globalConfig;
    }

    public AuctionConfirmGuiConfig getAuctionConfirmConfig() {
        return auctionConfirmConfig;
    }

    public ExpireGuiConfig getExpireConfig() {
        return expireConfig;
    }

    public PlayerViewConfig getPlayerViewConfig() {
        return playerViewConfig;
    }

    public CategoriesConfig getCategoriesConfig() {
        return categoriesConfig;
    }

    public HistoricConfig getHistoricConfig() {
        return historicConfig;
    }
}
