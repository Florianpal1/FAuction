package fr.florianpal.fauction.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.ClaimType;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.events.AuctionAddEvent;
import fr.florianpal.fauction.events.BidAddEvent;
import fr.florianpal.fauction.gui.subGui.*;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.ClaimManager;
import fr.florianpal.fauction.managers.SpamManager;
import fr.florianpal.fauction.managers.commandmanagers.AuctionCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.BidCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.ExpireCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.HistoricCommandManager;
import fr.florianpal.fauction.objects.Bid;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.utils.FormatUtil;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.ceil;

@CommandAlias("ah|hdv")
public class AuctionCommand extends BaseCommand {

    private final FAuction plugin;

    private final AuctionCommandManager auctionCommandManager;

    private final ExpireCommandManager expireCommandManager;

    private final HistoricCommandManager historicCommandManager;

    private final BidCommandManager bidCommandManager;

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
        this.bidCommandManager = plugin.getBidCommandManager();

        df = new DecimalFormat(plugin.getConfigurationManager().getGlobalConfig().getDecimalFormat());
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    @Default
    @Subcommand("list")
    @CommandPermission("fauction.list")
    @Description("{@@fauction.auction_list_help_description}")
    public void onList(Player playerSender) {

        if (spamManager.spamTest(playerSender, SpamAction.COMMAND)) {
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

        if (spamManager.spamTest(playerSender, SpamAction.COMMAND)) {
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

        if (spamManager.spamTest(playerSender, SpamAction.TRANSACTION)) {
            return;
        }

        // Reserved on the main thread : only one sale in flight per player, so the other packets of
        // the same tick cannot start a second sale of the very same item.
        long saleClaim = plugin.getClaimManager().tryClaim(playerSender.getUniqueId());
        if (saleClaim == ClaimManager.NOT_CLAIMED) {
            return;
        }

        ItemStack itemToSell = playerSender.getInventory().getItemInMainHand().clone();

        double price;
        if (globalConfig.isFeatureFlippingMoneyFormat()) {
            price = Double.parseDouble(df.format(priceEntry));
        } else {
            price = priceEntry;
        }

        // Everything is checked while the item is still in the inventory, so a refused sale has
        // nothing to give back.
        if (!isSellable(playerSender, itemToSell, price)) {
            plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim);
            return;
        }

        AtomicBoolean itemTaken = new AtomicBoolean(false);

        FAuction.newChain().asyncFirst(() -> plugin.getAuctionCommandManager().getAuctions(playerSender.getUniqueId())).syncLast(auctions -> {

            int limitations;
            if (plugin.getConfigurationManager().getGlobalConfig().isLimitationsUseMetaLuckperms()) {
                limitations = plugin.getLimitationManager().getAuctionLimitationByMeta(playerSender);
            } else {
                limitations = plugin.getLimitationManager().getAuctionLimitationByConfig(playerSender);
            }


            if (limitations != -1 && limitations <= auctions.size()) {
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_AUCTION);
                return;
            }

            // The hand may have changed during the round trip, only the checked item can be sold.
            ItemStack inHand = playerSender.getInventory().getItemInMainHand();
            if (!inHand.isSimilar(itemToSell) || inHand.getAmount() != itemToSell.getAmount()) {
                return;
            }

            if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                if (itemHash.contains((Integer) itemToSell.hashCode())) {
                    return;
                }
                itemHash.add((Integer) itemToSell.hashCode());
            }

            // Taken and resynchronised right away, so the client cannot keep a ghost item and send
            // it back to the server.
            playerSender.getInventory().setItemInMainHand(null);
            playerSender.updateInventory();
            itemTaken.set(true);

            FAuction.newChain().asyncFirst(() -> auctionCommandManager.addAuction(playerSender, itemToSell, price)).syncLast(added -> {

                // The hash only guards this sale while it is in flight ; it must not survive the
                // round trip, or every future sale of a visually identical item would be blocked
                // forever and the list would grow without bound.
                if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                    itemHash.remove((Integer) itemToSell.hashCode());
                }

                if (!Boolean.TRUE.equals(added)) {
                    plugin.getLogger().severe("Auction of " + playerSender.getName() + " could not be saved, item given back");
                    resetItem(playerSender, itemToSell);
                    return;
                }

                String itemName = itemToSell.getItemMeta().getDisplayName() == null || itemToSell.getItemMeta().getDisplayName().isEmpty() ? itemToSell.getType().toString() : itemToSell.getItemMeta().getDisplayName();
                plugin.getLogger().info("Player " + playerSender.getName() + " add item to ah Item : " + itemName + ", At Price : " + price);

                Bukkit.getPluginManager().callEvent(new AuctionAddEvent(playerSender, itemToSell, price));

                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_ADD_SUCCESS, "{item}", FormatUtil.titleItemFormat(itemToSell), "{price}", String.valueOf(price));
            }).execute(() -> plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim));

        }).execute(() -> {
            // The sale is released by the chain that really took the item, if any.
            if (!itemTaken.get()) {
                plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim);
            }
        });
    }

    /**
     * Every check done before the item leaves the inventory.
     */
    private boolean isSellable(Player playerSender, ItemStack itemToSell, double price) {

        if (itemToSell.getType().equals(Material.AIR)) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_AIR);
            return false;
        }

        if (price < 0) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.NEGATIVE_PRICE);
            return false;
        }

        if (globalConfig.getBlacklistItem().contains(itemToSell.getType())) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_BLACKLIST);
            return false;
        }

        if (!haveCorrectMinPrice(itemToSell, playerSender, price)) {
            return false;
        }

        if (!haveCorrectMaxPrice(itemToSell, playerSender, price)) {
            return false;
        }

        return haveCorrectShulkerPrice(playerSender, itemToSell, price);
    }

    public boolean haveCorrectShulkerPrice(Player playerSender, ItemStack itemToSell, double price) {

        if (!Tag.SHULKER_BOXES.getValues().contains(itemToSell.getType())) {
            return true;
        }

        if (!(itemToSell.getItemMeta() instanceof BlockStateMeta im) || !(im.getBlockState() instanceof ShulkerBox shulker)) {
            return true;
        }

        double minPrice = 0;
        double maxPrice = 0;
        boolean minPriceSet = false;
        boolean maxPriceSet = false;

        for (ItemStack itemIn : shulker.getInventory().getContents()) {
            if (itemIn != null && (itemIn.getType() != Material.AIR)) {
                if (globalConfig.getMinPrice().containsKey(itemIn.getType())) {
                    minPrice += itemIn.getAmount() * globalConfig.getMinPrice().get(itemIn.getType());
                    minPriceSet = true;
                } else if (globalConfig.isDefaultMinValueEnable()) {
                    minPrice += itemIn.getAmount() * globalConfig.getDefaultMinValue();
                    minPriceSet = true;
                }

                if (globalConfig.getMaxPrice().containsKey(itemIn.getType())) {
                    maxPrice += itemIn.getAmount() * globalConfig.getMaxPrice().get(itemIn.getType());
                    maxPriceSet = true;
                } else if (globalConfig.isDefaultMaxValueEnable()) {
                    maxPrice += itemIn.getAmount() * globalConfig.getDefaultMaxValue();
                    maxPriceSet = true;
                }
            }
        }

        if (minPriceSet && minPrice > price) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MIN_PRICE, "{minPrice}", String.valueOf(ceil(minPrice)));
            return false;
        }

        if (maxPriceSet && maxPrice < price) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_PRICE, "{maxPrice}", String.valueOf(ceil(maxPrice)));
            return false;
        }

        return true;
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

    /**
     * Gives an item back to a player. Never writes in the main hand, which would destroy whatever
     * the player put there meanwhile.
     */
    public void resetItem(Player playerSender, ItemStack item) {
        if (playerSender.getInventory().firstEmpty() == -1) {
            playerSender.getWorld().dropItem(playerSender.getLocation(), item);
        } else {
            playerSender.getInventory().addItem(item);
        }
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

    @Subcommand("bid")
    @CommandPermission("fauction.bid")
    @Description("{@@fauction.bid_help_description}")
    public void onBid(Player playerSender, double priceEntry) {

        if (spamManager.spamTest(playerSender, SpamAction.TRANSACTION)) {
            return;
        }

        // Reserved on the main thread, and kept separate from the classic sale claim : a player can
        // sell and start a bid in the very same tick without either blocking the other.
        long bidClaim = plugin.getClaimManager().tryClaimBid(playerSender.getUniqueId());
        if (bidClaim == ClaimManager.NOT_CLAIMED) {
            return;
        }

        ItemStack itemToSell = playerSender.getInventory().getItemInMainHand().clone();

        double startPrice;
        if (globalConfig.isFeatureFlippingMoneyFormat()) {
            startPrice = Double.parseDouble(df.format(priceEntry));
        } else {
            startPrice = priceEntry;
        }

        // Everything is checked while the item is still in the inventory, so a refused bid has
        // nothing to give back.
        if (!isBidStartable(playerSender, itemToSell, startPrice)) {
            plugin.getClaimManager().releaseBid(playerSender.getUniqueId(), bidClaim);
            return;
        }

        AtomicBoolean itemTaken = new AtomicBoolean(false);

        FAuction.newChain().asyncFirst(() -> bidCommandManager.getBids(playerSender.getUniqueId())).syncLast(bids -> {

            int limitations;
            if (plugin.getConfigurationManager().getGlobalConfig().isLimitationsUseMetaLuckperms()) {
                limitations = plugin.getLimitationManager().getBidLimitationByMeta(playerSender);
            } else {
                limitations = plugin.getLimitationManager().getBidLimitationByConfig(playerSender);
            }

            if (limitations != -1 && limitations <= bids.size()) {
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_BID);
                return;
            }

            // The hand may have changed during the round trip, only the checked item can be bid on.
            ItemStack inHand = playerSender.getInventory().getItemInMainHand();
            if (!inHand.isSimilar(itemToSell) || inHand.getAmount() != itemToSell.getAmount()) {
                return;
            }

            if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                if (itemHash.contains((Integer) itemToSell.hashCode())) {
                    return;
                }
                itemHash.add((Integer) itemToSell.hashCode());
            }

            // Taken and resynchronised right away, so the client cannot keep a ghost item and send
            // it back to the server.
            playerSender.getInventory().setItemInMainHand(null);
            playerSender.updateInventory();
            itemTaken.set(true);

            Date endDate = new Date(System.currentTimeMillis() + globalConfig.getBidTime() * 1000L);

            FAuction.newChain().asyncFirst(() -> bidCommandManager.addBid(playerSender, itemToSell, startPrice, endDate)).syncLast(added -> {

                if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                    itemHash.remove((Integer) itemToSell.hashCode());
                }

                if (!Boolean.TRUE.equals(added)) {
                    plugin.getLogger().severe("Bid of " + playerSender.getName() + " could not be saved, item given back");
                    resetItem(playerSender, itemToSell);
                    return;
                }

                String itemName = itemToSell.getItemMeta().getDisplayName() == null || itemToSell.getItemMeta().getDisplayName().isEmpty() ? itemToSell.getType().toString() : itemToSell.getItemMeta().getDisplayName();
                plugin.getLogger().info("Player " + playerSender.getName() + " started a bid on ah Item : " + itemName + ", At start price : " + startPrice);

                Bukkit.getPluginManager().callEvent(new BidAddEvent(playerSender, itemToSell, startPrice));

                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.BID_ADD_SUCCESS, "{item}", FormatUtil.titleItemFormat(itemToSell), "{price}", String.valueOf(startPrice));
            }).execute(() -> plugin.getClaimManager().releaseBid(playerSender.getUniqueId(), bidClaim));

        }).execute(() -> {
            // The bid is released by the chain that really took the item, if any.
            if (!itemTaken.get()) {
                plugin.getClaimManager().releaseBid(playerSender.getUniqueId(), bidClaim);
            }
        });
    }

    /**
     * Every check done before the item leaves the inventory to start a bid. The default min/max price
     * bounds only apply when bid.applyDefaultPriceLimits is enabled, a start price not needing to make
     * sense against the same bounds as an immediate sale.
     */
    boolean isBidStartable(Player playerSender, ItemStack itemToSell, double startPrice) {

        if (itemToSell.getType().equals(Material.AIR)) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_AIR);
            return false;
        }

        if (startPrice < 0) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.NEGATIVE_PRICE);
            return false;
        }

        if (globalConfig.getBlacklistItem().contains(itemToSell.getType())) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_BLACKLIST);
            return false;
        }

        if (!globalConfig.isBidApplyDefaultPriceLimits()) {
            return true;
        }

        if (!haveCorrectMinPrice(itemToSell, playerSender, startPrice)) {
            return false;
        }

        if (!haveCorrectMaxPrice(itemToSell, playerSender, startPrice)) {
            return false;
        }

        return haveCorrectShulkerPrice(playerSender, itemToSell, startPrice);
    }

    @Subcommand("bid list")
    @CommandPermission("fauction.bid")
    @Description("{@@fauction.bid_list_help_description}")
    public void onBidList(Player playerSender) {

        if (spamManager.spamTest(playerSender, SpamAction.COMMAND)) {
            return;
        }

        FAuction.newChain().asyncFirst(bidCommandManager::getBids).syncLast(bids -> {
            BidsGui bidsGui = new BidsGui(plugin, playerSender, bids, 1, null, null);
            bidsGui.initialize();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
        }).execute();
    }

    @Subcommand("bid cancel")
    @CommandPermission("fauction.bid")
    @Description("{@@fauction.bid_cancel_help_description}")
    public void onBidCancel(Player playerSender, int bidId) {

        if (spamManager.spamTest(playerSender, SpamAction.TRANSACTION)) {
            return;
        }

        // Ownership checked before reserving anything, exactly like AuctionsGui's right-click handling
        // ("a right click on someone else auction only opens a preview, no need to reserve") : a
        // player spamming /ah bid cancel on a bid he doesn't own never holds the claim, so he cannot
        // suppress another player's confirm click on it.
        FAuction.newChain().asyncFirst(() -> bidCommandManager.bidExist(bidId)).syncLast(existing -> {

            if (existing == null || !existing.getSellerUuid().equals(playerSender.getUniqueId())) {
                return;
            }

            long bidClaim = plugin.getClaimManager().tryClaim(ClaimType.BID, bidId);
            if (bidClaim == ClaimManager.NOT_CLAIMED) {
                return;
            }

            FAuction.newChain().asyncFirst(() -> bidCommandManager.claim(bidId)).syncLast(claimed -> {

                if (claimed == null) {
                    return;
                }

                // Checked again on the freshly claimed row, not the read above : a bid placed in the
                // window between the two (e.g. from another server sharing the same database) must
                // still block the cancellation instead of silently dropping the bidder's money and
                // item. Put back on the market rather than refunding-and-cancelling anyway, consistent
                // with a bid already engaged never being cancellable by its own seller.
                if (claimed.hasBidder()) {
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.BID_CANCEL_HAS_BIDDER);
                    bidCommandManager.restore(claimed);
                    return;
                }

                resetItem(playerSender, claimed.getItemStack());
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.BID_CANCEL_SUCCESS, "{item}", FormatUtil.titleItemFormat(claimed.getItemStack()));
            }).execute(() -> plugin.getClaimManager().release(ClaimType.BID, bidId, bidClaim));
        }).execute();
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

    @Subcommand("admin purge bid")
    @CommandPermission("fauction.admin.purge.bid")
    @Description("{@@fauction.reload_help_description}")
    public void onPurgeAllBid(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllBid();
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