package fr.florianpal.fauction.gui.subGui;

import co.aikar.commands.CommandIssuer;
import co.aikar.taskchain.TaskChain;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.AuctionConfirmGuiConfig;
import fr.florianpal.fauction.gui.AbstractGui;
import fr.florianpal.fauction.gui.GuiInterface;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.commandManagers.AuctionCommandManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Barrier;
import fr.florianpal.fauction.objects.Confirm;
import fr.florianpal.messagedif.MessageDif;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bukkit.Bukkit.getServer;

public class AuctionConfirmGui extends AbstractGui implements GuiInterface {

    private final Auction auction;
    protected final AuctionConfirmGuiConfig auctionConfirmConfig;
    private final AuctionCommandManager auctionCommandManager;
    private final Map<Integer, Confirm> confirmList = new HashMap<>();


    AuctionConfirmGui(FAuction plugin, Player player, int page, Auction auction) {
        super(plugin, player, page);
        this.auction = auction;
        this.auctionConfirmConfig = plugin.getConfigurationManager().getAuctionConfirmConfig();
        this.auctionCommandManager = new AuctionCommandManager(plugin);
        initGui(auctionConfirmConfig.getNameGui(), 27);
    }

    public void initializeItems() {

        for (Barrier barrier : auctionConfirmConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
        }

        int id = 0;
        for (Map.Entry<Integer, Confirm> entry : auctionConfirmConfig.getConfirmBlocks().entrySet()) {
            Confirm confirm = new Confirm(this.auction, entry.getValue().getMaterial(), entry.getValue().isValue());
            confirmList.put(entry.getKey(), confirm);
            inv.setItem(entry.getKey(), createGuiItem(confirm));
            id++;
            if (id >= (auctionConfirmConfig.getConfirmBlocks().size())) break;
        }
        openInventory(player);
    }

    private ItemStack createGuiItem(Confirm confirm) {
        ItemStack item = new ItemStack(confirm.getMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        String title = "";
        if (confirm.isValue()) {
            title = auctionConfirmConfig.getTitle_true();
        } else {
            title = auctionConfirmConfig.getTitle_false();
        }

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        ItemStack itemStack = confirm.getAuction().getItemStack().clone();
        if (confirm.getAuction().getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            title = title.replace("{Item}", itemStack.getType().toString());
        } else {
            title = title.replace("{Item}", itemStack.getItemMeta().getDisplayName());
        }
        title = title.replace("{Price}", df.format(confirm.getAuction().getPrice()));
        title = title.replace("{ProprietaireName}", confirm.getAuction().getPlayerName());

        title = ChatColor.translateAlternateColorCodes('&', title);
        List<String> listDescription = new ArrayList<>();
        for (String desc : auctionConfirmConfig.getDescription()) {
            desc = desc.replace("{Price}", df.format(confirm.getAuction().getPrice()));
            if (confirm.getAuction().getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{Item}", confirm.getAuction().getItemStack().getType().toString());
            } else {
                desc = desc.replace("{Item}", confirm.getAuction().getItemStack().getItemMeta().getDisplayName());
            }
            desc = desc.replace("{ProprietaireName}", confirm.getAuction().getPlayerName());

            desc = ChatColor.translateAlternateColorCodes('&', desc);
            listDescription.add(desc);
        }

        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(listDescription);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createGuiItem(Material material, String name, List<String> description) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        name = ChatColor.translateAlternateColorCodes('&', name);
        List<String> descriptions = new ArrayList<>();
        for (String desc : description) {
            desc = ChatColor.translateAlternateColorCodes('&', desc);
            descriptions.add(desc);
        }
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(descriptions);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (inv.getHolder() != this) {
            return;
        }
        if (!(e.getInventory() == inv)) {
            return;
        }
        e.setCancelled(true);
        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        for (Map.Entry<Integer, Confirm> entry : auctionConfirmConfig.getConfirmBlocks().entrySet()) {
            if (entry.getKey() == e.getRawSlot()) {
                CommandIssuer issuerTarget = commandManager.getCommandIssuer(player);
                Confirm confirm = confirmList.get(e.getRawSlot());
                if (!confirm.isValue()) {
                    issuerTarget.sendInfo(MessageKeys.BUY_AUCTION_CANCELLED);
                    player.getOpenInventory().close();
                    AuctionsGui auctionsGui = new AuctionsGui(plugin, player, 1);
                    auctionsGui.initializeItems();
                    return;
                }

                TaskChain<Auction> chainAuction = auctionCommandManager.auctionExist(this.auction.getId());
                chainAuction.sync(() -> {
                    if (chainAuction.getTaskData("auction") == null) {
                        issuerTarget.sendInfo(MessageKeys.NO_AUCTION);
                        return;
                    }
                    TaskChain<Auction> chainAuction2 = auctionCommandManager.auctionExist(this.auction.getId());
                    chainAuction2.sync(() -> {
                        if (chainAuction2.getTaskData("auction") == null) {
                            issuerTarget.sendInfo(MessageKeys.AUCTION_ALREADY_SELL);
                            return;
                        }
                        issuerTarget.sendInfo(MessageKeys.BUY_AUCTION_SUCCESS);
                        auctionCommandManager.deleteAuction(auction.getId());
                        plugin.getVaultIntegrationManager().getEconomy().withdrawPlayer(player, auction.getPrice());
                        EconomyResponse economyResponse4 = plugin.getVaultIntegrationManager().getEconomy().depositPlayer(Bukkit.getOfflinePlayer(auction.getPlayerUuid()), auction.getPrice());
                        if (!economyResponse4.transactionSuccess()) {
                            return;
                        }

                        if (player.getInventory().firstEmpty() == -1) {
                            player.getWorld().dropItem(player.getLocation(), auction.getItemStack());
                        } else {
                            player.getInventory().addItem(auction.getItemStack());
                        }

                        if (plugin.getConfigurationManager().getGlobalConfig().isOnBuyMessageUse()) {
                            String message = plugin.getConfigurationManager().getGlobalConfig().getOnBuyMessage();
                            message = message.replace("{OwnerName}", auction.getPlayerName());
                            message = message.replace("{Amount}", String.valueOf(auction.getItemStack().getAmount()));
                            if (!auction.getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                                message = message.replace("{ItemName}", auction.getItemStack().getItemMeta().getDisplayName());
                            } else {
                                message = message.replace("{ItemName}", auction.getItemStack().getType().name().replace('_', ' ').toLowerCase());
                            }
                            message = message.replace("{BuyerName}", player.getName());
                            message = message.replace("{ItemPrice}", String.valueOf(auction.getPrice()));
                            MessageDif.newMessage(auction.getPlayerUuid(), message);
                        }

                        Bukkit.getLogger().info("Player : " + player.getName() + " buy " + auction.getItemStack().getI18NDisplayName() + " at " + auction.getPlayerName());

                        player.getOpenInventory().close();
                        AuctionsGui auctionsGui = new AuctionsGui(plugin, player, 1);
                        auctionsGui.initializeItems();
                    }).execute();

                }).execute();
                break;
            }
        }
    }
}
