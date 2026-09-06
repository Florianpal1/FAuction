package fr.florianpal.fauction.utils;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.Gui;
import fr.florianpal.fauction.gui.visualization.InventoryVisualization;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Bid;
import org.bukkit.Bukkit;
import org.bukkit.block.Barrel;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.BlockStateMeta;

public class VisualizationUtils {

    public static void createVizualisation(FAuction plugin, Auction auction, Player player, Gui gui) {
        InventoryVisualization inventoryVisualization = null;
        if (ItemUtil.isShulker(auction.getItemStack())) {

            BlockStateMeta blockStateMeta = (BlockStateMeta) auction.getItemStack().getItemMeta();
            ShulkerBox shulkerBox = (ShulkerBox) blockStateMeta.getBlockState();
            inventoryVisualization = new InventoryVisualization(plugin, player, shulkerBox, gui, auction);
        } else if (ItemUtil.isBarrel(auction.getItemStack())) {

            BlockStateMeta blockStateMeta = (BlockStateMeta) auction.getItemStack().getItemMeta();
            Barrel barrel = (Barrel) blockStateMeta.getBlockState();
            inventoryVisualization = new InventoryVisualization(plugin, player, barrel, gui, auction);
        }

        if (inventoryVisualization != null) {
            Bukkit.getPluginManager().registerEvents(inventoryVisualization, plugin);
            inventoryVisualization.open();
        }
    }

    public static void createVizualisation(FAuction plugin, Bid bid, Player player, Gui gui) {
        InventoryVisualization inventoryVisualization = null;
        if (ItemUtil.isShulker(bid.getItemStack())) {

            BlockStateMeta blockStateMeta = (BlockStateMeta) bid.getItemStack().getItemMeta();
            ShulkerBox shulkerBox = (ShulkerBox) blockStateMeta.getBlockState();
            inventoryVisualization = new InventoryVisualization(plugin, player, shulkerBox, gui, bid);
        } else if (ItemUtil.isBarrel(bid.getItemStack())) {

            BlockStateMeta blockStateMeta = (BlockStateMeta) bid.getItemStack().getItemMeta();
            Barrel barrel = (Barrel) blockStateMeta.getBlockState();
            inventoryVisualization = new InventoryVisualization(plugin, player, barrel, gui, bid);
        }

        if (inventoryVisualization != null) {
            Bukkit.getPluginManager().registerEvents(inventoryVisualization, plugin);
            inventoryVisualization.open();
        }
    }
}
