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
import fr.florianpal.fauction.configurations.SortConfig;
import fr.florianpal.fauction.configurations.gui.*;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class ConfigurationManager {

    @Getter
    private final DatabaseConfig database = new DatabaseConfig();
    private final YamlDocument databaseConfig;

    @Getter
    private final AuctionConfig auctionConfig = new AuctionConfig();
    private YamlDocument auctionConfiguration;

    @Getter
    private final HistoricConfig historicConfig = new HistoricConfig();
    private YamlDocument historicConfiguration;

    @Getter
    private final PlayerViewConfig playerViewConfig = new PlayerViewConfig();
    private YamlDocument playerViewConfiguration;

    @Getter
    private final ExpireGuiConfig expireConfig = new ExpireGuiConfig();
    private YamlDocument expireConfiguration;

    @Getter
    private final AuctionConfirmGuiConfig auctionConfirmConfig = new AuctionConfirmGuiConfig();
    private YamlDocument auctionConfirmConfiguration;

    @Getter
    private final GlobalConfig globalConfig = new GlobalConfig();
    private YamlDocument globalConfiguration;

    @Getter
    private final CategoriesConfig categoriesConfig = new CategoriesConfig();
    private YamlDocument categoriesConfiguration;

    @Getter
    private final SortConfig sortConfig = new SortConfig();
    private YamlDocument sortConfiguration;

    @Getter
    private final MenuConfig menuConfig = new MenuConfig();

    public ConfigurationManager(FAuction plugin) {

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
    }

    public void reload(FAuction plugin) {
        loadAllConfiguration(plugin);
    }

    private void loadAllConfiguration(FAuction plugin) {

        try {

            auctionConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/auction.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/gui/auction.yml")),
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(false).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            historicConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/historic.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/gui/historic.yml")),
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(false).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            playerViewConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/playerView.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/gui/playerView.yml")),
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(false).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            expireConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/expire.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/gui/expire.yml")),
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(false).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            auctionConfirmConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "gui/auctionConfirm.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/gui/auctionConfirm.yml")),
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(false).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            globalConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "config.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            categoriesConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "categories.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/categories.yml")),
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );

            sortConfiguration = YamlDocument.create(new File(plugin.getDataFolder(), "sort.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/sort.yml")),
                    GeneralSettings.builder().setUseDefaults(false).build(),
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
            );
        } catch (IOException e) {
            plugin.getLogger().severe("Error in configuration load : " + e.getMessage());
            throw new RuntimeException(e);
        }

        categoriesConfig.load(categoriesConfiguration);
        sortConfig.load(sortConfiguration);
        globalConfig.load(globalConfiguration);
        auctionConfig.load(plugin, auctionConfiguration);
        historicConfig.load(plugin, historicConfiguration);
        auctionConfirmConfig.load(plugin, auctionConfirmConfiguration);
        expireConfig.load(plugin, expireConfiguration);
        playerViewConfig.load(plugin, playerViewConfiguration);

        File menusDir = new File(plugin.getDataFolder().getPath() + "/gui/menus");
        if (!menusDir.exists()){
            menusDir.mkdirs();
        }

        menuConfig.load(plugin);
    }
}
