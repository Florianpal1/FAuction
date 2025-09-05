package fr.florianpal.fauction.gui.subGui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.gui.AbstractGuiWithAuctions;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.objects.Sort;
import fr.florianpal.fauction.utils.ListUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class HistoricGui extends AbstractGuiWithAuctions {

    public HistoricGui(FAuction plugin, Player player, List<Auction> auctions, int page, Category category, Sort sort) {
        super(plugin, player, page, auctions, category, sort, plugin.getConfigurationManager().getHistoricConfig());
    }

    @Override
    protected void previousAction() {
        FAuction.newChain().asyncFirst(() -> historicCommandManager.getHistorics(player.getUniqueId())).syncLast(historics -> {
            HistoricGui gui = new HistoricGui(plugin, player, ListUtil.historicToAuction(historics), this.page - 1, category, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void nextAction() {
        FAuction.newChain().asyncFirst(() -> historicCommandManager.getHistorics(player.getUniqueId())).syncLast(historics -> {
            HistoricGui gui = new HistoricGui(plugin, player, ListUtil.historicToAuction(historics), this.page + 1, category, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void categoryAction(Category nextCategory) {
        FAuction.newChain().asyncFirst(() -> historicCommandManager.getHistorics(player.getUniqueId())).syncLast(historics -> {
            HistoricGui gui = new HistoricGui(plugin, player, ListUtil.historicToAuction(historics), 1 , nextCategory, sort);
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

        guiClick(e);
    }
}