package fr.florianpal.fauction.gui.visualization;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.Gui;
import fr.florianpal.fauction.gui.subGui.AuctionConfirmGui;
import fr.florianpal.fauction.gui.subGui.AuctionsGui;
import fr.florianpal.fauction.objects.Auction;
import org.bukkit.Bukkit;
import org.bukkit.block.Barrel;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryVisualization implements InventoryHolder, Listener {

    private static final Logger log = LoggerFactory.getLogger(InventoryVisualization.class);
    private final FAuction plugin;

    private Inventory inventory;

    private final Player player;

    private final Gui gui;

    private final Auction auction;

    private boolean isClosed = false;

    public InventoryVisualization(FAuction plugin, Player player, ShulkerBox shulkerBox, Gui gui, Auction auction) {
        this.player = player;
        this.plugin = plugin;
        this.gui = gui;
        this.auction = auction;
        createInventory(shulkerBox.getInventory(), InventoryType.SHULKER_BOX);
    }

    public InventoryVisualization(FAuction plugin, Player player, Barrel barrel, Gui gui, Auction auction) {
        this.player = player;
        this.plugin = plugin;
        this.gui = gui;
        this.auction = auction;
        createInventory(barrel.getInventory(), InventoryType.BARREL);
    }

    private void createInventory(Inventory oldInventory, InventoryType inventoryType) {

        inventory = Bukkit.createInventory(this, inventoryType);
        inventory.setContents(oldInventory.getContents());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClickEvent(InventoryClickEvent event) {

        if (event.getInventory().getHolder() != this) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    private void onInventoryClose(InventoryCloseEvent event) {

        if (event.getInventory().getHolder() != this) {
            return;
        }

        if (isClosed) {
            return;
        }
        isClosed = true;

        if (Gui.AUCTION.equals(gui)) {
            FAuction.newChain().asyncFirst(() -> plugin.getAuctionCommandManager().getAuctions()).syncLast(auctions -> {
                AuctionsGui auctionsGui = new AuctionsGui(plugin, player, auctions, 1, null, null);
                auctionsGui.initialize();
            }).execute();
        } else if (Gui.CONFIRM.equals(gui)) {
            FAuction.newChain().sync(() -> {
                AuctionConfirmGui auctionConfirmGui = new AuctionConfirmGui(plugin, player, 1, auction);
                auctionConfirmGui.initialize();
            }).execute();
        }
    }
}
