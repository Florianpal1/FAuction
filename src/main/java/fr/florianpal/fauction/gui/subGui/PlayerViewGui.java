package fr.florianpal.fauction.gui.subGui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.gui.PlayerViewConfig;
import fr.florianpal.fauction.enums.CancelReason;
import fr.florianpal.fauction.events.AuctionCancelEvent;
import fr.florianpal.fauction.gui.AbstractGuiWithAuctions;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PlayerViewGui extends AbstractGuiWithAuctions {

    private final PlayerViewConfig playerViewConfig;
    private static final Set<Integer> processingAuctions = new HashSet<>();

    public PlayerViewGui(FAuction plugin, Player player, List<Auction> auctions, int page, Category category) {
        super(plugin, player, page, auctions, category, plugin.getConfigurationManager().getPlayerViewConfig());
        this.playerViewConfig = plugin.getConfigurationManager().getPlayerViewConfig();
        this.auctions = auctions.stream().filter(a -> a.getPlayerUUID().equals(player.getUniqueId())).collect(Collectors.toList());
    }

    @Override
    protected void previousAction() {
        FAuction.newChain().asyncFirst(() -> auctionCommandManager.getAuctions(player.getUniqueId())).syncLast(auctions -> {
            PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions, this.page - 1, category);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void nextAction() {
        FAuction.newChain().asyncFirst(() -> auctionCommandManager.getAuctions(player.getUniqueId())).syncLast(auctions -> {
            PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions, this.page + 1, category);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void categoryAction(Category nextCategory) {
        FAuction.newChain().asyncFirst(() -> auctionCommandManager.getAuctions(player.getUniqueId())).syncLast(auctions -> {
            PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions,1 , nextCategory);
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

        if (spamManager.spamTest(player)) {
            return;
        }

        for (int index : playerViewConfig.getBaseBlocks()) {
            if (index == e.getRawSlot()) {
                int nb0 = playerViewConfig.getBaseBlocks().get(0);
                int nb = (e.getRawSlot() - nb0) / 9;
                Auction auction = auctions.get((e.getRawSlot() - nb0) + ((this.playerViewConfig.getBaseBlocks().size() * this.page) - this.playerViewConfig.getBaseBlocks().size()) - nb * 2);

                if (e.isRightClick()) {
                    int auctionId = auction.getId();
                    // Prevent processing the same auction multiple times
                    if (processingAuctions.contains(auctionId)) {
                        return;
                    }
                    processingAuctions.add(auctionId);
                    
                    FAuction.newChain().asyncFirst(() -> auctionCommandManager.auctionExist(auctionId))
                        .abortIfNull()
                        .abortIf(a -> !a.getPlayerUUID().equals(player.getUniqueId()))
                        .syncLast(a -> {

                        try {
                            auctionCommandManager.deleteAuction(a.getId());

                            if (player.getInventory().firstEmpty() == -1) {
                                player.getWorld().dropItem(player.getLocation(), a.getItemStack());
                            } else {
                                player.getInventory().addItem(a.getItemStack());
                            }


                            plugin.getLogger().info("Player delete from ah auction : " + a.getId() + ", Item : " + a.getItemStack().getItemMeta().getDisplayName() + " of " + a.getPlayerName() + ", by" + player.getName());

                            auctions.remove(a);
                            Bukkit.getPluginManager().callEvent(new AuctionCancelEvent(player, a, CancelReason.PLAYER));

                            MessageUtil.sendMessage(plugin, player, MessageKeys.REMOVE_AUCTION_SUCCESS);
                        } catch (Exception exception) {
                            plugin.getLogger().severe(exception.toString());
                        }
                        player.closeInventory();

                        FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                            processingAuctions.remove(auctionId);
                            PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions, this.page, category);
                            gui.initialize();
                        }).execute();
                    }).abortIfNull()
                      .execute(() -> processingAuctions.remove(auctionId));
                }
                break;
            }
        }
    }
}