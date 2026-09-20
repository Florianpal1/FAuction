package fr.florianpal.fauction;

import com.tcoded.folialib.FoliaLib;
import fr.florianpal.fauction.commands.AuctionCommand;
import fr.florianpal.fauction.enums.CommandKey;
import fr.florianpal.fauction.enums.MigrateVersion;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.*;
import fr.florianpal.fauction.managers.commandmanagers.*;
import fr.florianpal.fauction.languages.Lang;
import fr.florianpal.fauction.managers.implementations.LuckPermsImplementation;
import fr.florianpal.fauction.placeholders.FPlaceholderExpansion;
import fr.florianpal.fauction.queries.AuctionQueries;
import fr.florianpal.fauction.queries.CurrencyPendingQueries;
import fr.florianpal.fauction.queries.ExpireQueries;
import fr.florianpal.fauction.queries.HistoricQueries;
import fr.florianpal.fauction.schedules.CacheSchedule;
import fr.florianpal.fauction.schedules.CurrencyScheduler;
import fr.florianpal.fauction.schedules.ExpireSchedule;
import fr.florianpal.fauction.utils.FormatUtil;
import fr.florianpal.fauction.utils.scheduling.SchedulerChain;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import me.seetch.mlang.MLang;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.*;

public class FAuction extends JavaPlugin {

    private static FAuction api;

    private static FoliaLib foliaLib;

    @Getter
    private ConfigurationManager configurationManager;

    @Getter
    private AuctionQueries auctionQueries;

    @Getter
    private ExpireQueries expireQueries;

    @Getter
    private HistoricQueries historicQueries;

    @Getter
    private CurrencyPendingQueries currencyPendingQueries;

    @Getter
    private CommandManager commandManager;

    @Getter
    private Lang lang;

    @Getter
    private VaultIntegrationManager vaultIntegrationManager;

    @Getter
    private DatabaseManager databaseManager;

    @Getter
    private LimitationManager limitationManager;

    @Getter
    private AuctionCommandManager auctionCommandManager;

    @Getter
    private ExpireCommandManager expireCommandManager;

    @Getter
    private HistoricCommandManager historicCommandManager;

    @Getter
    private SpamManager spamManager;

    @Getter
    private ClaimManager claimManager;

    @Getter
    private TransfertManager transfertManager;

    private Metrics metrics;

    @Getter
    private LuckPermsImplementation luckPermsImplementation;

    @Getter
    private boolean placeholderAPIEnabled = false;

    private WrappedTask expireTask;

    private WrappedTask cacheTask;

    private WrappedTask currencyTask;

    /**
     * A chain whose sync step runs on the global region ; use {@link #newChain(Entity)} instead
     * when the sync step touches a specific player/entity (e.g. their inventory).
     */
    public static SchedulerChain<Void> newChain() {
        return SchedulerChain.newChain(foliaLib, api.getLogger());
    }

    /**
     * A chain whose sync step runs on the region owning {@code entity}, even if that region moves
     * under Folia.
     */
    public static SchedulerChain<Void> newChain(Entity entity) {
        return SchedulerChain.newChain(foliaLib, api.getLogger(), entity);
    }

    public static FoliaLib getFoliaLib() {
        return foliaLib;
    }

    @Getter
    private MLang mLang;

    @Override
    public void onEnable() {

        metrics = new Metrics(this, 24018);
        PaperLib.suggestPaper(this);

        foliaLib = new FoliaLib(this);

        try {
            configurationManager = new ConfigurationManager(this);
            configurationManager.reload(this);
        } catch (RuntimeException e) {
            getLogger().severe(e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (configurationManager.getGlobalConfig().isLimitationsUseMetaLuckperms()) {
            luckPermsImplementation = new LuckPermsImplementation();
        }

        lang = new Lang();
        lang.load(this);

        commandManager = new CommandManager(this);

        limitationManager = new LimitationManager(this);

        vaultIntegrationManager = new VaultIntegrationManager(this);

        try {
            databaseManager = new DatabaseManager(this);
        } catch (SQLException e) {
            getLogger().severe(e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        auctionQueries = new AuctionQueries(this);
        expireQueries = new ExpireQueries(this);
        historicQueries = new HistoricQueries(this);
        currencyPendingQueries = new CurrencyPendingQueries(this);

        databaseManager.addRepository(expireQueries);
        databaseManager.addRepository(auctionQueries);
        databaseManager.addRepository(historicQueries);
        databaseManager.addRepository(currencyPendingQueries);
        databaseManager.initializeTables();

        auctionCommandManager = new AuctionCommandManager(this);
        expireCommandManager = new ExpireCommandManager(this);
        historicCommandManager = new HistoricCommandManager(this);

        spamManager = new SpamManager(this);
        claimManager = new ClaimManager();
        transfertManager = new TransfertManager(this);

        commandManager.register(new AuctionCommand(this));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FPlaceholderExpansion(this).register();
            placeholderAPIEnabled = true;
        }

        if (configurationManager.getGlobalConfig().isFeatureFlippingExpiration()) {
            long checkEvery = configurationManager.getGlobalConfig().getCheckEvery();
            expireTask = foliaLib.getScheduler().runTimer(new ExpireSchedule(this), checkEvery, checkEvery);
        }
        if (configurationManager.getGlobalConfig().isFeatureFlippingCacheUpdate()) {
            long updateCacheEvery = configurationManager.getGlobalConfig().getUpdateCacheEvery();
            cacheTask = foliaLib.getScheduler().runTimer(new CacheSchedule(this), updateCacheEvery, updateCacheEvery);
        }

        long checkEveryCurrency = configurationManager.getGlobalConfig().getCheckEveryCurrency();
        currencyTask = foliaLib.getScheduler().runTimer(new CurrencyScheduler(this), checkEveryCurrency, checkEveryCurrency);

        api = this;

        initChart();

        mLang = MLang.getInstance(this);

        String language = FormatUtil.formatLanguageCode(configurationManager.getGlobalConfig().getLang());
        String version = FormatUtil.formatServerVersion();

        mLang.setDefaultLanguage(language);
        mLang.setDefaultVersion(version);

        mLang.loadDefaultLanguageAsync().thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                getLogger().info("Language " + language + " for version " + version + " loaded successfully!");
            } else {
                getLogger().warning("Failed to load language " + language + " for version " + version);
            }
        }).exceptionally(throwable -> {
            getLogger().severe("Error loading language: " + throwable.getMessage());
            return null;
        });
    }

    @Override
    public void onDisable() {

        // onEnable can call disablePlugin() before databaseManager is assigned (config or database
        // initialization failure) ; this runs synchronously as part of that call. configurationManager
        // is always set by this point, onEnable returning before databaseManager is ever reached
        // otherwise.
        if (databaseManager == null) {
            return;
        }

        if (expireTask != null) {
            expireTask.cancel();
        }
        if (cacheTask != null) {
            cacheTask.cancel();
        }
        if (currencyTask != null) {
            currencyTask.cancel();
        }
        if (spamManager != null) {
            spamManager.shutdown();
        }

        if (configurationManager.getDatabase().getSqlType().equals(SQLType.SQLite)) {
            auctionCommandManager.deleteAllOnlyOnDB();
            auctionCommandManager.saveAllAuctionInBddFromSQLiteCache();
        }

        databaseManager.close();
    }

    public static FAuction getApi() {
        return api;
    }

    private void initChart() {
        metrics.addCustomChart(new AdvancedPie("player_per_country", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            List<Player> onlinePlayers = new ArrayList<>(Bukkit.getServer().getOnlinePlayers());

            int count = 0;
            for (Player player : onlinePlayers) {
                if (player.isValid() && player.isOnline()) {
                    count = count + 1;
                }
            }

            valueMap.put(TimeZone.getDefault().getID(), count);
            return valueMap;
        }));
    }

    /**
     * Reloads every configuration file and the language file.
     *
     * @return whether the names of the commands changed. Bukkit only takes the commands a plugin
     * registers while it is enabling, so the new names cannot answer before the server is
     * restarted ; saying nothing would leave the administrator typing a command that does not
     * exist yet.
     */
    public boolean reloadConfiguration() {

        Map<CommandKey, List<String>> namesBefore = configurationManager.getCommandsConfig().snapshot();

        configurationManager.reload(this);
        lang.load(this);

        boolean commandNamesChanged = !namesBefore.equals(configurationManager.getCommandsConfig().snapshot());
        if (commandNamesChanged) {
            getLogger().warning("The commands section of config.yml changed. The commands are registered when the "
                    + "server starts : restart it for the new names to answer.");
        }

        return commandNamesChanged;
    }

    public void purgeAllData() {
        auctionCommandManager.deleteAll();
        expireCommandManager.deleteAll();
        historicCommandManager.deleteAll();
    }

    public void purgeAllExpire() {
        expireCommandManager.deleteAll();
    }

    public void purgeAllAuction() {
        auctionCommandManager.deleteAll();
    }

    public void purgeAllHistoric() {
        historicCommandManager.deleteAll();
    }

    /**
     * Runs the migration of a version the plugin knows about. The version is validated by the command
     * parser, so a version that was never handled cannot reach this point and be reported as a
     * success ; a new constant added without its case here fails loudly instead of doing nothing.
     */
    public void migrate(MigrateVersion migrateVersion) {

        switch (migrateVersion) {
            case V_1_7_8 -> historicQueries.addBuyDate();
            default -> throw new IllegalStateException("Unhandled migration version " + migrateVersion);
        }
    }
}