package fr.florianpal.fauction.gui.subGui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.gui.PlayerViewConfig;
import fr.florianpal.fauction.enums.CancelReason;
import fr.florianpal.fauction.enums.ClaimType;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.events.AuctionCancelEvent;
import fr.florianpal.fauction.gui.AbstractGuiWithAuctions;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.ClaimManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.objects.Sort;
import fr.florianpal.fauction.utils.FormatUtil;
import fr.florianpal.fauction.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public class PlayerViewGui extends AbstractGuiWithAuctions {

    private final PlayerViewConfig playerViewConfig;

    public PlayerViewGui(FAuction plugin, Player player, List<Auction> auctions, int page, Category category, Sort sort) {
        super(plugin, player, page, auctions, category, sort, plugin.getConfigurationManager().getPlayerViewConfig());
        this.playerViewConfig = plugin.getConfigurationManager().getPlayerViewConfig();
        this.auctions = auctions.stream().filter(a -> a.getPlayerUUID().equals(player.getUniqueId())).collect(Collectors.toList());
    }

    @Override
    protected void previousAction() {
        FAuction.newChain().asyncFirst(() -> auctionCommandManager.getAuctions(player.getUniqueId())).syncLast(auctions -> {
            PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions, this.page - 1, category, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void nextAction() {
        FAuction.newChain().asyncFirst(() -> auctionCommandManager.getAuctions(player.getUniqueId())).syncLast(auctions -> {
            PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions, this.page + 1, category, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void categoryAction(Category nextCategory) {
        FAuction.newChain().asyncFirst(() -> auctionCommandManager.getAuctions(player.getUniqueId())).syncLast(auctions -> {
            PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions,1 , nextCategory, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void sortingAction(Sort nextSort) {
        FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
            AuctionsGui gui = new AuctionsGui(plugin, player, auctions, 1, category, nextSort);
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

        for (var index : playerViewConfig.getBaseBlocks().entrySet()) {
            if (index.getValue() == e.getRawSlot()) {

                int itemIndex = ((index.getKey() + 1) + (this.playerViewConfig.getBaseBlocks().size() * (this.page - 1))) - 1;
                Auction auction = auctions.get(itemIndex);

                if (e.isRightClick()) {
                    int auctionId = auction.getId();

                    // Reserved on the main thread : every other click of the same tick stops here,
                    // before being able to schedule a second chain on the same auction.
                    long auctionClaim = plugin.getClaimManager().tryClaim(ClaimType.AUCTION, auctionId);
                    if (auctionClaim == ClaimManager.NOT_CLAIMED) {
                        return;
                    }

                    if (!auction.getPlayerUUID().equals(player.getUniqueId())) {
                        plugin.getClaimManager().release(ClaimType.AUCTION, auctionId, auctionClaim);
                        return;
                    }

                    FAuction.newChain().asyncFirst(() -> auctionCommandManager.claim(auctionId)).syncLast(a -> {

                        // The row is already gone : nothing can be handed over twice from here.
                        if (a == null) {
                            return;
                        }

                        try {

                            if (player.getInventory().firstEmpty() == -1) {
                                player.getWorld().dropItem(player.getLocation(), a.getItemStack());
                            } else {
                                player.getInventory().addItem(a.getItemStack());
                            }


                            plugin.getLogger().info("Player delete from ah auction : " + a.getId() + ", Item : " + a.getItemStack().getItemMeta().getDisplayName() + " of " + a.getPlayerName() + ", by" + player.getName());

                            auctions.remove(auction);
                            Bukkit.getPluginManager().callEvent(new AuctionCancelEvent(player, a, CancelReason.PLAYER));

                            MessageUtil.sendMessage(plugin, player, MessageKeys.REMOVE_AUCTION_SUCCESS, "{item}", FormatUtil.titleItemFormat(a.getItemStack()));
                        } catch (Exception exception) {
                            plugin.getLogger().severe(exception.toString());
                        }
                        player.closeInventory();

                        FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctionsNew -> {
                            PlayerViewGui gui = new PlayerViewGui(plugin, player, auctionsNew, this.page, category, sort);
                            gui.initialize();
                        }).execute();
                    }).execute(() -> plugin.getClaimManager().release(ClaimType.AUCTION, auctionId, auctionClaim));
                }
                break;
            }
        }
    }
}