package fr.florianpal.fauction.gui.subGui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.gui.BidConfirmGuiConfig;
import fr.florianpal.fauction.enums.ClaimType;
import fr.florianpal.fauction.enums.Gui;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.events.BidPlaceEvent;
import fr.florianpal.fauction.gui.AbstractGuiWithBids;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.ClaimManager;
import fr.florianpal.fauction.objects.*;
import fr.florianpal.fauction.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.*;

public class BidConfirmGui extends AbstractGuiWithBids {

    private final Bid bid;

    protected final BidConfirmGuiConfig bidConfirmConfig;

    private final Map<Integer, BidConfirm> confirmList = new HashMap<>();

    public BidConfirmGui(FAuction plugin, Player player, int page, Bid bid) {
        super(plugin, player, page, Collections.singletonList(bid), null, null, plugin.getConfigurationManager().getBidConfirmConfig());
        this.bid = bid;
        this.bidConfirmConfig = plugin.getConfigurationManager().getBidConfirmConfig();
    }

    public void initialize() {

        initGui(abstractGuiConfig.getNameGui(), abstractGuiConfig.getSize());
        initBarrier();

        for (Barrier barrier : bidConfirmConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), getItemStack(barrier, false));
        }

        for (var index : bidConfirmConfig.getBaseBlocks().entrySet()) {
            inv.setItem(index.getValue(), createGuiItem(bid));
        }

        int id = 0;
        for (Confirm entry : bidConfirmConfig.getConfirmBlocks()) {
            BidConfirm confirm = new BidConfirm(entry.getIndex(), this.bid,
                    entry.getMaterial(),
                    entry.isValue(),
                    entry.getTexture(),
                    entry.getCustomModelData()
            );
            confirmList.put(entry.getIndex(), confirm);
            inv.setItem(entry.getIndex(), createGuiItem(confirm));
            id++;
            if (id >= (bidConfirmConfig.getConfirmBlocks().size())) break;
        }
        openInventory(player);
    }

    private double nextBidAmount() {
        return bid.getCurrentPrice() + globalConfig.getBidMinIncrement();
    }

    private ItemStack createGuiItem(BidConfirm confirm) {
        ItemStack item = new ItemStack(confirm.getMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        String title;
        if (confirm.isValue()) {
            title = bidConfirmConfig.getTitleTrue();
        } else {
            title = bidConfirmConfig.getTitleFalse();
        }

        title = FormatUtil.titleItemFormat(confirm.getBid().getItemStack(), "{Item}", title);

        title = title.replace("{Price}", df.format(nextBidAmount()));
        title = title.replace("{OwnerName}", confirm.getBid().getSellerName());

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(bid.getSellerUuid());
        if (offlinePlayer != null) {
            title = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, title);
        }

        title = FormatUtil.format(title);
        List<String> listDescription = new ArrayList<>();
        for (String desc : bidConfirmConfig.getDescription()) {
            desc = desc.replace("{Price}", df.format(nextBidAmount()));
            desc = desc.replace("{CurrentBid}", df.format(confirm.getBid().getCurrentPrice()));
            desc = FormatUtil.titleItemFormat(confirm.getBid().getItemStack(), "{Item}", desc);
            desc = desc.replace("{OwnerName}", confirm.getBid().getSellerName());

            if (offlinePlayer != null) {
                desc = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, desc);
            }

            desc = FormatUtil.format(desc);
            listDescription.add(desc);
        }

        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(listDescription);
            meta.setCustomModelData(confirm.getCustomModelData());
            item.setItemMeta(meta);
        }
        if (confirm.getMaterial() == Material.PLAYER_HEAD) {
            PlayerHeadUtil.addTexture(item, confirm.getTexture());
            item.setAmount(1);
        }
        return item;
    }

    @Override
    public ItemStack createGuiItem(Bid bid) {
        ItemStack item = bid.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        String title = bidConfirmConfig.getBidTitle();
        if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            title = title.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
        } else {
            title = title.replace("{ItemName}", item.getItemMeta().getDisplayName());
        }
        title = title.replace("{OwnerName}", bid.getSellerName());
        title = title.replace("{CurrentBid}", df.format(bid.getCurrentPrice()));
        title = title.replace("{Price}", df.format(nextBidAmount()));

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(bid.getSellerUuid());
        if (offlinePlayer != null) {
            title = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, title);
        }

        title = FormatUtil.format(title);
        List<String> listDescription = new ArrayList<>();

        for (String desc : bidConfirmConfig.getBidDescription()) {
            if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
            } else {
                desc = desc.replace("{ItemName}", item.getItemMeta().getDisplayName());
            }

            desc = desc.replace("{OwnerName}", bid.getSellerName());

            if (offlinePlayer != null) {
                desc = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, desc);
            }

            desc = desc.replace("{CurrentBid}", df.format(bid.getCurrentPrice()));
            desc = desc.replace("{Price}", df.format(nextBidAmount()));
            desc = desc.replace("{ExpireTime}", dateFormater.format(bid.getEndDate()));

            Duration duration = Duration.between(new Date().toInstant(), bid.getEndDate().toInstant());
            desc = desc.replace("{RemainingTime}", FormatUtil.durationFormat(globalConfig.getRemainingDateFormat(), duration));
            if (desc.contains("lore")) {
                if (item.getItemMeta().getLore() != null) {
                    listDescription.addAll(item.getItemMeta().getLore());
                } else {
                    listDescription.add(desc.replace("{lore}", ""));
                }
            } else {
                desc = FormatUtil.format(desc);
                listDescription.add(desc);
            }
        }
        if (meta != null) {
            if (bidConfirmConfig.isReplaceTitle()) {
                meta.setDisplayName(title);
            }
            meta.setLore(listDescription);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    protected void previousAction() {
    }

    @Override
    protected void nextAction() {
    }

    @Override
    protected void categoryAction(Category nextCategory) {
    }

    @Override
    protected void sortingAction(Sort nextSort) {
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {

        if (e.getInventory() != inv || inv.getHolder() != this || player != e.getWhoClicked()) {
            return;
        }
        e.setCancelled(true);

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (guiClick(e)) {
            return;
        }

        if (spamManager.spamTest(player, SpamAction.TRANSACTION)) {
            return;
        }

        boolean isBaseBlock = abstractGuiWithBidsConfig.getBaseBlocks().values().stream().anyMatch(b -> b == e.getRawSlot());
        if (isBaseBlock) {

            VisualizationUtils.createVizualisation(plugin, bid, player, Gui.BID_CONFIRM);
            return;
        }

        for (BidConfirm entry : confirmList.values()) {
            if (entry.getIndex() == e.getRawSlot()) {

                if (!entry.isValue()) {
                    MessageUtil.sendMessage(plugin, player, MessageKeys.BID_CANCELLED);
                    FAuction.newChain().asyncFirst(bidCommandManager::getBids).syncLast(bids -> {
                        BidsGui gui = new BidsGui(plugin, player, bids, 1, null, null);
                        gui.initialize();
                    }).execute();
                    return;
                }

                placeBid();
                break;
            }
        }
    }

    private void placeBid() {

        int bidId = this.bid.getId();
        double previousPrice = this.bid.getCurrentPrice();
        UUID previousBidderUuid = this.bid.getCurrentBidderUuid();
        String previousBidderName = this.bid.getCurrentBidderName();
        double newPrice = nextBidAmount();

        // Reserved on the main thread : every other click of the same tick stops here, before being
        // able to schedule a second chain on the same bid.
        long bidClaim = plugin.getClaimManager().tryClaim(ClaimType.BID, bidId);
        if (bidClaim == ClaimManager.NOT_CLAIMED) {
            return;
        }

        // Read only check, so the usual "not enough money" case never touches the bid row nor debits
        // anything.
        if (!CurrencyUtil.haveCurrency(plugin, player, globalConfig.getCurrencyType(), newPrice)) {
            MessageUtil.sendMessage(plugin, player, MessageKeys.NO_HAVE_MONEY);
            plugin.getClaimManager().release(ClaimType.BID, bidId, bidClaim);
            return;
        }

        // Debited before the row is ever touched (unlike a first read-then-write-then-debit order),
        // so a raise that turns out to be rejected below is a plain, immediate refund instead of
        // having to unwind a row that already shows a bidder who was never actually charged.
        if (!CurrencyUtil.getCurrency(plugin, player, globalConfig.getCurrencyType(), newPrice)) {
            MessageUtil.sendMessage(plugin, player, MessageKeys.NO_HAVE_MONEY);
            plugin.getClaimManager().release(ClaimType.BID, bidId, bidClaim);
            return;
        }

        FAuction.newChain().asyncFirst(() -> bidCommandManager.placeBid(bidId, player.getUniqueId(), player.getName(), previousPrice, newPrice)).syncLast(placed -> {

            if (!Boolean.TRUE.equals(placed)) {
                // Someone else raised the bid (or BidSchedule ended it) since this gui was opened :
                // give the debit straight back, nothing else was ever touched.
                if (!CurrencyUtil.giveCurrency(plugin, player, globalConfig.getCurrencyType(), newPrice)) {
                    plugin.getLogger().severe("Refund of " + player.getName() + "'s rejected bid of " + df.format(newPrice) + " on " + FormatUtil.titleItemFormat(bid.getItemStack()) + " failed ; manual correction required.");
                }
                MessageUtil.sendMessage(plugin, player, MessageKeys.BID_TOO_LOW);
                return;
            }

            if (previousBidderUuid != null) {
                OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(previousBidderUuid);
                if (!CurrencyUtil.giveCurrency(plugin, previousBidder, globalConfig.getCurrencyType(), previousPrice)) {
                    plugin.getLogger().severe("Refund of outbid player " + previousBidderName + " (" + df.format(previousPrice) + ") on " + FormatUtil.titleItemFormat(bid.getItemStack()) + " failed ; manual correction required.");
                }

                if (previousBidder.isOnline()) {
                    MessageUtil.sendMessage(plugin, previousBidder.getPlayer(), MessageKeys.OUTBID_NOTIFICATION, "{item}", FormatUtil.titleItemFormat(bid.getItemStack()), "{price}", df.format(newPrice));
                }
            }

            plugin.getLogger().info("Player : " + player.getName() + " bid " + df.format(newPrice) + " on " + FormatUtil.titleItemFormat(bid.getItemStack()) + " of " + bid.getSellerName());

            Bukkit.getPluginManager().callEvent(new BidPlaceEvent(player, bid, newPrice));

            MessageUtil.sendMessage(plugin, player, MessageKeys.BID_PLACED_SUCCESS, "{item}", FormatUtil.titleItemFormat(bid.getItemStack()), "{price}", df.format(newPrice));

            FAuction.newChain().asyncFirst(bidCommandManager::getBids).syncLast(bids -> {
                BidsGui gui = new BidsGui(plugin, player, bids, 1, null, null);
                gui.initialize();
            }).execute();
        }).execute(() -> plugin.getClaimManager().release(ClaimType.BID, bidId, bidClaim));
    }
}
