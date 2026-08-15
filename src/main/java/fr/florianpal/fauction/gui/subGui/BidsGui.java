package fr.florianpal.fauction.gui.subGui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.gui.BidConfig;
import fr.florianpal.fauction.enums.CancelReason;
import fr.florianpal.fauction.enums.ClaimType;
import fr.florianpal.fauction.enums.Gui;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.events.BidCancelEvent;
import fr.florianpal.fauction.gui.AbstractGuiWithBids;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.ClaimManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Bid;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.objects.Sort;
import fr.florianpal.fauction.utils.CurrencyUtil;
import fr.florianpal.fauction.utils.FormatUtil;
import fr.florianpal.fauction.utils.MessageUtil;
import fr.florianpal.fauction.utils.SerializationUtil;
import fr.florianpal.fauction.utils.VisualizationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BidsGui extends AbstractGuiWithBids {

    private final BidConfig bidConfig;

    public BidsGui(FAuction plugin, Player player, List<Bid> bids, int page, Category category, Sort sort) {
        super(plugin, player, page, bids, category, sort, plugin.getConfigurationManager().getBidConfig());
        this.bidConfig = plugin.getConfigurationManager().getBidConfig();
    }

    @Override
    protected void previousAction() {
        FAuction.newChain().asyncFirst(bidCommandManager::getBids).syncLast(bids -> {
            BidsGui gui = new BidsGui(plugin, player, bids, this.page - 1, category, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void nextAction() {
        FAuction.newChain().asyncFirst(bidCommandManager::getBids).syncLast(bids -> {
            BidsGui gui = new BidsGui(plugin, player, bids, this.page + 1, category, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void categoryAction(Category nextCategory) {
        FAuction.newChain().asyncFirst(bidCommandManager::getBids).syncLast(bids -> {
            BidsGui gui = new BidsGui(plugin, player, bids, 1, nextCategory, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void sortingAction(Sort nextSort) {
        FAuction.newChain().asyncFirst(bidCommandManager::getBids).syncLast(bids -> {
            BidsGui gui = new BidsGui(plugin, player, bids, 1, category, nextSort);
            gui.initialize();
        }).execute();
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

        if (spamManager.spamTest(player, SpamAction.INTERACT)) {
            return;
        }

        for (var index : bidConfig.getBaseBlocks().entrySet()) {
            if (index.getValue() == e.getRawSlot()) {
                int itemIndex = ((index.getKey() + 1) + (this.bidConfig.getBaseBlocks().size() * (this.page - 1))) - 1;
                Bid bid = bids.get(itemIndex);

                if (e.isRightClick()) {
                    handleCancel(bid, e.isShiftClick());
                } else if (e.isLeftClick()) {
                    handleBid(bid);
                }
                break;
            }
        }
    }

    private void handleCancel(Bid bid, boolean isShiftClick) {

        // Shift-click required, exactly like the classic auction cancel : a plain right-click by a
        // moderator must still be able to open the preview instead of always cancelling the listing.
        boolean isModCanCancel = isShiftClick && player.hasPermission("fauction.mod.bid.cancel");
        boolean isOwner = bid.getSellerUuid().equals(player.getUniqueId());

        if (!isOwner && !isModCanCancel) {
            VisualizationUtils.createVizualisation(plugin, bid, player, Gui.BID);
            return;
        }

        int bidId = bid.getId();

        long bidClaim = plugin.getClaimManager().tryClaim(ClaimType.BID, bidId);
        if (bidClaim == ClaimManager.NOT_CLAIMED) {
            return;
        }

        FAuction.newChain().asyncFirst(() -> bidCommandManager.claim(bidId)).syncLast(b -> {

            if (b == null) {
                return;
            }

            // Checked again on the freshly claimed row, not the gui's possibly stale snapshot (in a
            // shared-database mode, a bid could have been placed after this gui was built) : a bid
            // already engaged cannot be cancelled by its own seller, there being no refund path for
            // the current bidder from that action ; only a moderator override goes through with a
            // bidder.
            if (isOwner && !isModCanCancel && b.hasBidder()) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.BID_CANCEL_HAS_BIDDER);
                bidCommandManager.restore(b);
                return;
            }

            try {
                if (b.hasBidder()) {
                    OfflinePlayer bidder = Bukkit.getOfflinePlayer(b.getCurrentBidderUuid());
                    if (!CurrencyUtil.giveCurrency(plugin, bidder, globalConfig.getCurrencyType(), b.getCurrentPrice())) {
                        plugin.getLogger().severe("Refund of " + b.getCurrentBidderName() + " (" + b.getCurrentPrice() + ") on cancelled bid " + b.getId() + " failed ; manual correction required.");
                    }
                }

                if (isModCanCancel) {
                    // The item goes back to its owner, exactly like a moderator cancel of a classic
                    // auction, never into the moderator's own inventory.
                    Auction asExpire = new Auction(0, b.getSellerUuid(), b.getSellerName(), b.getStartPrice(), SerializationUtil.serialize(b.getItemStack()), b.getStartDate().getTime());
                    if (!plugin.getExpireCommandManager().addExpire(asExpire)) {
                        plugin.getLogger().severe("Bid " + b.getId() + " of " + b.getSellerName() + " could not be moved to the expired items by moderator " + player.getName() + " ; put back on the market instead of being lost.");
                        bidCommandManager.restore(b);
                        return;
                    }
                } else if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItem(player.getLocation(), b.getItemStack());
                } else {
                    player.getInventory().addItem(b.getItemStack());
                }

                CancelReason reason = isModCanCancel ? CancelReason.MODERATOR : CancelReason.PLAYER;
                plugin.getLogger().info((isModCanCancel ? "Modo" : "Player") + " cancel bid : " + b.getId() + ", Item : " + FormatUtil.titleItemFormat(b.getItemStack()) + " of " + b.getSellerName() + ", by " + player.getName());
                Bukkit.getPluginManager().callEvent(new BidCancelEvent(player, b, reason));

                bids.remove(bid);

                MessageUtil.sendMessage(plugin, player, MessageKeys.BID_CANCEL_SUCCESS, "{item}", FormatUtil.titleItemFormat(b.getItemStack()));
            } catch (Exception exception) {
                plugin.getLogger().severe(exception.toString());
            }

            player.closeInventory();

            FAuction.newChain().asyncFirst(bidCommandManager::getBids).syncLast(bidsNew -> {
                BidsGui gui = new BidsGui(plugin, player, bidsNew, 1, category, sort);
                gui.initialize();
            }).execute();
        }).execute(() -> plugin.getClaimManager().release(ClaimType.BID, bidId, bidClaim));
    }

    private void handleBid(Bid bid) {
        if (bid.getSellerUuid().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(plugin, player, MessageKeys.BID_YOUR_ITEM);
            return;
        }

        if (bid.hasBidder() && bid.getCurrentBidderUuid().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(plugin, player, MessageKeys.BID_ALREADY_YOURS);
            return;
        }

        double nextBid = bid.getCurrentPrice() + globalConfig.getBidMinIncrement();
        if (!CurrencyUtil.haveCurrency(plugin, player, globalConfig.getCurrencyType(), nextBid)) {
            MessageUtil.sendMessage(plugin, player, MessageKeys.NO_HAVE_MONEY);
            return;
        }

        BidConfirmGui bidConfirmGui = new BidConfirmGui(plugin, player, page, bid);
        bidConfirmGui.initialize();
    }
}
