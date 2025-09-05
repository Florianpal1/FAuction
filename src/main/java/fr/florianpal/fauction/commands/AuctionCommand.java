package fr.florianpal.fauction.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.events.AuctionAddEvent;
import fr.florianpal.fauction.gui.subGui.*;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.SpamManager;
import fr.florianpal.fauction.managers.commandmanagers.AuctionCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.ExpireCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.HistoricCommandManager;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.utils.ListUtil;
import fr.florianpal.fauction.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.lang.Math.ceil;

@CommandAlias("ah|hdv")
public class AuctionCommand extends BaseCommand {

    private final FAuction plugin;

    private final AuctionCommandManager auctionCommandManager;

    private final ExpireCommandManager expireCommandManager;

    private final HistoricCommandManager historicCommandManager;

    private final SpamManager spamManager;

    private final GlobalConfig globalConfig;

    private List<Integer> itemHash = new ArrayList<>();

    protected DecimalFormat df;

    public AuctionCommand(FAuction plugin) {
        this.plugin = plugin;
        this.auctionCommandManager = plugin.getAuctionCommandManager();
        this.expireCommandManager = plugin.getExpireCommandManager();
        this.spamManager = plugin.getSpamManager();
        this.globalConfig = plugin.getConfigurationManager().getGlobalConfig();
        this.historicCommandManager = plugin.getHistoricCommandManager();

        df = new DecimalFormat(plugin.getConfigurationManager().getGlobalConfig().getDecimalFormat());
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    @Default
    @Subcommand("list")
    @CommandPermission("fauction.list")
    @Description("{@@fauction.auction_list_help_description}")
    public void onList(Player playerSender) {

        if (spamManager.spamTest(playerSender)) {
            return;
        }

        switch (globalConfig.getDefaultGui()) {
            case "AUCTION":
                FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                    AuctionsGui auctionsGui = new AuctionsGui(plugin, playerSender, auctions, 1, null, null);
                    auctionsGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "EXPIRE":
                FAuction.newChain().asyncFirst(() -> expireCommandManager.getExpires(playerSender.getUniqueId())).syncLast(expires -> {
                    ExpireGui expireGui = new ExpireGui(plugin, playerSender, expires, 1, null, null);
                    expireGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "HISTORIC":
                FAuction.newChain().asyncFirst(() -> historicCommandManager.getHistorics(playerSender.getUniqueId())).syncLast(historics -> {
                    HistoricGui historicGui = new HistoricGui(plugin, playerSender, ListUtil.historicToAuction(historics), 1, null, null);
                    historicGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "PLAYER":
                FAuction.newChain().asyncFirst(() -> auctionCommandManager.getAuctions(playerSender.getUniqueId())).syncLast(auctions -> {
                    PlayerViewGui playerViewGui = new PlayerViewGui(plugin, playerSender, auctions, 1, null, null);
                    playerViewGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            default:
                String[] id = globalConfig.getDefaultGui().split(":");

                if (!"MENU".equals(id[0])) {
                    return;
                }

                MainGui gui = new MainGui(plugin, id[1], playerSender, 1);
                gui.initialize();
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                break;

        }

    }

    @Subcommand("search")
    @CommandPermission("fauction.search")
    @Description("{@@fauction.auction_search_help_description}")
    public void onSearch(Player playerSender, Material material) {

        if (spamManager.spamTest(playerSender)) {
            return;
        }

        if (material.isAir()) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SEARCH_AIR);
            return;
        }

        FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
            AuctionsGui auctionsGui = new AuctionsGui(plugin, playerSender, auctions, 1, new Category("-1", material.name(), List.of(material.toString())), null);
            auctionsGui.initialize();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
        }).execute();
    }

    @Subcommand("sell")
    @CommandPermission("fauction.sell")
    @Description("{@@fauction.auction_add_help_description}")
    public void onAdd(Player playerSender, double priceEntry) {

        if (spamManager.spamTest(playerSender)) {
            return;
        }

        ItemStack itemToSell = playerSender.getInventory().getItemInMainHand().clone();

        if (globalConfig.isFeatureDuplicationHashCodeControl()) {
            if (itemHash.contains((Integer) itemToSell.hashCode())) {
                return;
            }
            itemHash.add((Integer) itemToSell.hashCode());
        }

        playerSender.getInventory().getItemInMainHand().setAmount(0);

        double price;
        if (globalConfig.isFeatureFlippingMoneyFormat()) {
            price = Double.parseDouble(df.format(priceEntry));
        } else {
            price = priceEntry;
        }

        if (price < 0) {
            resetItem(playerSender, itemToSell);
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.NEGATIVE_PRICE);
            return;
        }

        if (itemToSell.getType().equals(Material.AIR)) {
            resetItem(playerSender, itemToSell);
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_AIR);
            return;
        }

        if (globalConfig.getBlacklistItem().contains(itemToSell.getType())) {
            resetItem(playerSender, itemToSell);
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_BLACKLIST);
            return;
        }

        if (!haveCorrectMinPrice(itemToSell, playerSender, price)) {
            resetItem(playerSender, itemToSell);
            return;
        }

        if (!haveCorrectMaxPrice(itemToSell, playerSender, price)) {
            resetItem(playerSender, itemToSell);
            return;
        }

        if (Tag.SHULKER_BOXES.getValues().contains(itemToSell.getType())) {
            ItemStack item = playerSender.getInventory().getItemInMainHand();
            if (item.getItemMeta() instanceof BlockStateMeta im) {

                double minPrice = -1;
                double maxPrice = -1;
                if (im.getBlockState() instanceof ShulkerBox shulker) {

                    for (ItemStack itemIn : shulker.getInventory().getContents()) {
                        if (itemIn != null && (itemIn.getType() != Material.AIR)) {
                            if (plugin.getConfigurationManager().getGlobalConfig().getMinPrice().containsKey(itemIn.getType())) {
                                minPrice = minPrice + itemIn.getAmount() * globalConfig.getMinPrice().get(itemIn.getType());
                            } else if (plugin.getConfigurationManager().getGlobalConfig().isDefaultMinValueEnable()) {
                                minPrice = minPrice + itemIn.getAmount() * globalConfig.getDefaultMinValue();
                            }

                            if (plugin.getConfigurationManager().getGlobalConfig().getMaxPrice().containsKey(itemIn.getType())) {
                                maxPrice = maxPrice + itemIn.getAmount() * globalConfig.getMaxPrice().get(itemIn.getType());
                            } else if (plugin.getConfigurationManager().getGlobalConfig().isDefaultMaxValueEnable()) {
                                maxPrice = maxPrice + itemIn.getAmount() * globalConfig.getDefaultMaxValue();
                            }
                        }
                    }
                    if (minPrice != -1 && minPrice > price) {
                        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MIN_PRICE, "{minPrice}", String.valueOf(ceil(minPrice)));
                        resetItem(playerSender, itemToSell);
                        return;
                    }

                    if (maxPrice != -1 && maxPrice < price) {
                        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_PRICE, "{maxPrice}", String.valueOf(ceil(maxPrice)));
                        resetItem(playerSender, itemToSell);
                        return;
                    }
                }
            }
        }

        FAuction.newChain().asyncFirst(() -> plugin.getAuctionCommandManager().getAuctions(playerSender.getUniqueId())).syncLast(auctions -> {

            int limitations;
            if (plugin.getConfigurationManager().getGlobalConfig().isLimitationsUseMetaLuckperms()) {
                limitations = plugin.getLimitationManager().getAuctionLimitationByMeta(playerSender);
            } else {
                limitations = plugin.getLimitationManager().getAuctionLimitationByConfig(playerSender);
            }


            if (limitations != -1 && limitations <= auctions.size()) {
                resetItem(playerSender, itemToSell);
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_AUCTION);
                return;
            }

            FAuction.newChain().async(() -> auctionCommandManager.addAuction(playerSender, itemToSell, price)).sync(() -> {

                String itemName = itemToSell.getItemMeta().getDisplayName() == null || itemToSell.getItemMeta().getDisplayName().isEmpty() ? itemToSell.getType().toString() : itemToSell.getItemMeta().getDisplayName();
                plugin.getLogger().info("Player " + playerSender.getName() + " add item to ah Item : " + itemName + ", At Price : " + price);

                Bukkit.getPluginManager().callEvent(new AuctionAddEvent(playerSender, itemToSell, price));

                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_ADD_SUCCESS);
            }).execute();

        }).execute();
    }

    public boolean haveCorrectMinPrice(ItemStack itemToSell, Player player, double price) {

        if (globalConfig.getMinPrice().containsKey(itemToSell.getType())) {
            double minPrice = itemToSell.getAmount() * globalConfig.getMinPrice().get(itemToSell.getType());
            if (minPrice > price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MIN_PRICE, "{minPrice}", String.valueOf(ceil(minPrice)));
                return false;
            }
        } else if (globalConfig.isDefaultMinValueEnable()) {
            double minPrice = itemToSell.getAmount() * globalConfig.getDefaultMinValue();
            if (minPrice > price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MIN_PRICE, "{minPrice}", String.valueOf(ceil(minPrice)));
                return false;
            }
        }

        return true;
    }

    public void resetItem(Player playerSender, ItemStack item) {
        playerSender.getInventory().setItemInMainHand(item);
        itemHash.remove((Integer)item.hashCode());
    }

    public boolean haveCorrectMaxPrice(ItemStack itemToSell, Player player, double price) {

        if (globalConfig.getMaxPrice().containsKey(itemToSell.getType())) {
            double maxPrice = itemToSell.getAmount() * globalConfig.getMaxPrice().get(itemToSell.getType());
            if (maxPrice < price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MAX_PRICE, "{maxPrice}", String.valueOf(ceil(maxPrice)));
                return false;
            }
        } else if (globalConfig.isDefaultMaxValueEnable()) {
            double maxPrice = itemToSell.getAmount() * globalConfig.getDefaultMaxValue();
            if (maxPrice < price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MAX_PRICE, "{maxPrice}", String.valueOf(ceil(maxPrice)));
                return false;
            }
        }
        return true;
    }

    @Subcommand("expire")
    @CommandPermission("fauction.expire")
    @Description("{@@fauction.expire_add_help_description}")
    public void onExpire(Player playerSender) {

        FAuction.newChain().asyncFirst(() -> expireCommandManager.getExpires(playerSender.getUniqueId())).syncLast(auctions -> {
            ExpireGui gui = new ExpireGui(plugin, playerSender, auctions, 1, null, null);
            gui.initialize();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
        }).execute();
    }

    @Subcommand("admin reload")
    @CommandPermission("fauction.admin.reload")
    @Description("{@@fauction.reload_help_description}")
    public void onReload(Player playerSender) {

        plugin.reloadConfiguration();
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_RELOAD);
    }

    @Subcommand("admin purge all")
    @CommandPermission("fauction.admin.purge.all")
    @Description("{@@fauction.reload_help_description}")
    public void onPurgeAll(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllData();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Subcommand("admin purge historic")
    @CommandPermission("fauction.admin.purge.hictoric")
    @Description("{@@fauction.reload_help_description}")
    public void onPurgeAllHistoric(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllHistoric();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Subcommand("admin purge expire")
    @CommandPermission("fauction.admin.purge.expire")
    @Description("{@@fauction.reload_help_description}")
    public void onPurgeAllExpire(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllExpire();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Subcommand("admin purge auction")
    @CommandPermission("fauction.admin.purge.auction")
    @Description("{@@fauction.reload_help_description}")
    public void onPurgeAllAucton(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllAuction();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Subcommand("admin transfertToPaper")
    @CommandPermission("fauction.admin.transfertBddToPaper")
    @Description("{@@fauction.transfert_bdd_help_description}")
    public void onTransferBddPaper(Player playerSender) {

        plugin.getTransfertManager().transfertBDD(true);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.TRANSFERT_BDD);
    }

    @Subcommand("admin transfertToBukkit")
    @CommandPermission("fauction.admin.transfertBddToPaper")
    @Description("{@@fauction.transfert_bdd_help_description}")
    public void onTransferBddSpigot(Player playerSender) {

        plugin.getTransfertManager().transfertBDD(false);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.TRANSFERT_BDD);
    }

    @Subcommand("admin migrate")
    @CommandPermission("fauction.admin.migrate")
    @Description("{@@fauction.migrate_help_description}")
    public void onMigrate(Player playerSender, String migrateVersion) {

        plugin.migrate(migrateVersion);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MIGRATE, "{version}", migrateVersion);
    }

    @HelpCommand
    @Description("{@@fauction.help_description}")
    public void doHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }
}