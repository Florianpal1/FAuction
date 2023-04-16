
/*
 * Copyright (C) 2022 Florianpal
 *
 * This program is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses/>.
 *
 * Last modification : 07/01/2022 23:07
 *
 *  @author Florianpal.
 */

package fr.florianpal.fauction.gui.subGui;

import co.aikar.commands.CommandIssuer;
import co.aikar.taskchain.TaskChain;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.ConfirmGuiConfig;
import fr.florianpal.fauction.enums.InterfaceType;
import fr.florianpal.fauction.enums.ViewType;
import fr.florianpal.fauction.gui.AbstractGui;
import fr.florianpal.fauction.gui.GuiInterface;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.commandManagers.AuctionCommandManager;
import fr.florianpal.fauction.managers.commandManagers.BillCommandManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Barrier;
import fr.florianpal.fauction.objects.Bill;
import fr.florianpal.fauction.objects.Confirm;
import fr.florianpal.fauction.utils.FormatUtil;
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

public class ConfirmGui extends AbstractGui implements GuiInterface {

    private Auction auction;

    private Bill bill;

    private double newBet;
    protected final ConfirmGuiConfig confirmConfig;
    private final AuctionCommandManager auctionCommandManager;
    private final BillCommandManager billCommandManager;
    private final Map<Integer, Confirm> confirmList = new HashMap<>();

    private final InterfaceType interfaceType;


    ConfirmGui(FAuction plugin, Player player, int page, Auction auction, InterfaceType interfaceType) {
        super(plugin, player, page);
        this.auction = auction;
        this.confirmConfig = plugin.getConfigurationManager().getConfirmConfig();
        this.auctionCommandManager = plugin.getAuctionCommandManager();
        this.billCommandManager = plugin.getBillCommandManager();
        this.interfaceType = interfaceType;
        initGui(confirmConfig.getNameGui(), 27);
    }

    ConfirmGui(FAuction plugin, Player player, int page, Bill bill, double newBet, InterfaceType interfaceType) {
        super(plugin, player, page);
        this.bill = bill;
        this.confirmConfig = plugin.getConfigurationManager().getConfirmConfig();
        this.auctionCommandManager = plugin.getAuctionCommandManager();
        this.billCommandManager = plugin.getBillCommandManager();
        this.interfaceType = interfaceType;
        this.newBet = newBet;
        initGui(confirmConfig.getNameGui(), 27);
    }

    public void initializeItems() {

        for (Barrier barrier : confirmConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
        }

        int id = 0;
        for (Map.Entry<Integer, Confirm> entry : confirmConfig.getConfirmBlocks().entrySet()) {
            Confirm confirm;
            if(interfaceType == InterfaceType.AUCTION) {
                confirm = new Confirm(this.auction, entry.getValue().getMaterial(), entry.getValue().isValue());
            } else {
                confirm = new Confirm(this.bill, entry.getValue().getMaterial(), entry.getValue().isValue());
            }
            confirmList.put(entry.getKey(), confirm);
            inv.setItem(entry.getKey(), createGuiItem(confirm));
            id++;
            if (id >= (confirmConfig.getConfirmBlocks().size())) break;
        }
        openInventory(player);
    }

    private ItemStack createGuiItem(Confirm confirm) {
        ItemStack item = new ItemStack(confirm.getMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        String title = "";
        if (confirm.isValue()) {
            title = confirmConfig.getTitleTrue();
        } else {
            title = confirmConfig.getTitleFalse();
        }

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);

        List<String> listDescription = new ArrayList<>();
        if (interfaceType == InterfaceType.AUCTION) {
            ItemStack itemStack = confirm.getAuction().getItemStack().clone();
            if (confirm.getAuction().getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                title = title.replace("{Item}", itemStack.getType().toString());
            } else {
                title = title.replace("{Item}", itemStack.getItemMeta().getDisplayName());
            }
            title = title.replace("{Price}", df.format(confirm.getAuction().getPrice()));
            title = title.replace("{ProprietaireName}", confirm.getAuction().getPlayerName());

            title = FormatUtil.format(title);

            for (String desc : confirmConfig.getDescription()) {
                desc = desc.replace("{Price}", df.format(confirm.getAuction().getPrice()));
                if (confirm.getAuction().getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                    desc = desc.replace("{Item}", confirm.getAuction().getItemStack().getType().toString());
                } else {
                    desc = desc.replace("{Item}", confirm.getAuction().getItemStack().getItemMeta().getDisplayName());
                }
                desc = desc.replace("{ProprietaireName}", confirm.getAuction().getPlayerName());

                desc = FormatUtil.format(desc);
                listDescription.add(desc);
            }
        } else if (interfaceType == InterfaceType.BILL) {
            ItemStack itemStack = confirm.getBill().getItemStack().clone();
            if (confirm.getBill().getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                title = title.replace("{Item}", itemStack.getType().toString());
            } else {
                title = title.replace("{Item}", itemStack.getItemMeta().getDisplayName());
            }
            title = title.replace("{Price}", df.format(newBet));
            title = title.replace("{ProprietaireName}", confirm.getBill().getPlayerName());
            if(bill.getPlayerBidderName() != null) {
                title = title.replace("{BidderName}", bill.getPlayerBidderName());
            } else {
                title = title.replace("{BidderName}", "Personne");
            }
            title = FormatUtil.format(title);

            for (String desc : confirmConfig.getDescription()) {

                if (confirm.getBill().getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                    desc = desc.replace("{Item}", confirm.getBill().getItemStack().getType().toString());
                } else {
                    desc = desc.replace("{Item}", confirm.getBill().getItemStack().getItemMeta().getDisplayName());
                }
                desc = desc.replace("{ProprietaireName}", confirm.getBill().getPlayerName());
                desc = desc.replace("{Price}", df.format(newBet));

                if(bill.getPlayerBidderName() != null) {
                    desc = desc.replace("{BidderName}", bill.getPlayerBidderName());
                } else {
                    desc = desc.replace("{BidderName}", "Personne");
                }

                desc = FormatUtil.format(desc);
                listDescription.add(desc);
            }
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
        name = FormatUtil.format(name);
        List<String> descriptions = new ArrayList<>();
        for (String desc : description) {
            desc = FormatUtil.format(desc);
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
        if (inv.getHolder() != this || e.getInventory() != inv) {
            return;
        }
        e.setCancelled(true);
        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (interfaceType == InterfaceType.AUCTION) {

            for (Map.Entry<Integer, Confirm> entry : confirmConfig.getConfirmBlocks().entrySet()) {
                if (entry.getKey() == e.getRawSlot()) {
                    CommandIssuer issuerTarget = commandManager.getCommandIssuer(player);
                    Confirm confirm = confirmList.get(e.getRawSlot());
                    if (!confirm.isValue()) {
                        issuerTarget.sendInfo(MessageKeys.BUY_AUCTION_CANCELLED);
                        player.getOpenInventory().close();
                        MainGui auctionsGui = new MainGui(plugin, player, ViewType.ALL, 1, interfaceType);
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

                            if (plugin.getConfigurationManager().getGlobalConfig().isAuctionOnBuyCommandUse()) {
                                String message = plugin.getConfigurationManager().getGlobalConfig().getAuctionOnBuyMessage();
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
                            MainGui auctionsGui = new MainGui(plugin, player, ViewType.ALL, 1, interfaceType);
                            auctionsGui.initializeItems();
                        }).execute();

                    }).execute();
                    break;
                }
            }
        } else if (interfaceType == InterfaceType.BILL) {
            for (Map.Entry<Integer, Confirm> entry : confirmConfig.getConfirmBlocks().entrySet()) {
                if (entry.getKey() == e.getRawSlot()) {
                    CommandIssuer issuerTarget = commandManager.getCommandIssuer(player);
                    Confirm confirm = confirmList.get(e.getRawSlot());
                    if (!confirm.isValue()) {
                        issuerTarget.sendInfo(MessageKeys.BUY_BILL_CANCELLED);
                        player.getOpenInventory().close();
                        MainGui mainsGui = new MainGui(plugin, player, ViewType.ALL, 1, interfaceType);
                        mainsGui.initializeItems();
                        return;
                    }

                    TaskChain<Bill> chainBet = billCommandManager.billExist(this.bill.getId());
                    chainBet.sync(() -> {
                        if (chainBet.getTaskData("bill") == null) {
                            issuerTarget.sendInfo(MessageKeys.NO_BILL);
                            return;
                        }

                        if(newBet <= this.bill.getBet()) {
                            issuerTarget.sendInfo(MessageKeys.AMOUNT_LESS_THAN_BET, "{Offer}", String.valueOf(newBet), "{Bet}", String.valueOf(this.bill.getBet()));
                            return;
                        }

                        TaskChain<Bill> chainBet2 = billCommandManager.billExist(this.bill.getId());
                        chainBet2.sync(() -> {

                            if (chainBet2.getTaskData("bill") == null) {
                                issuerTarget.sendInfo(MessageKeys.BILL_ALREADY_SELL);
                                return;
                            }

                            issuerTarget.sendInfo(MessageKeys.MAKE_OFFER_BILL_SUCCESS, "{Offer}", String.valueOf(newBet));

                            billCommandManager.makeOffer(bill.getId(), player, newBet);

                            Bukkit.getLogger().info("Player : " + player.getName() + " make offer for " + bill.getItemStack().getI18NDisplayName() + " at " + bill.getPlayerName());

                            player.getOpenInventory().close();
                            MainGui auctionsGui = new MainGui(plugin, player, ViewType.ALL, 1, interfaceType);
                            auctionsGui.initializeItems();
                        }).execute();

                    }).execute();
                    break;
                }
            }
        }
    }
}
