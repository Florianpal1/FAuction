
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
import fr.florianpal.fauction.configurations.MainGuiConfig;
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
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainGui extends AbstractGui implements GuiInterface {
    private List<Auction> auctions = new ArrayList<>();

    private List<Bill> bills = new ArrayList<>();

    protected final AuctionCommandManager auctionCommandManager;
    protected final BillCommandManager billCommandManager;
    private final MainGuiConfig mainGuiConfig;
    private final ViewType viewType;

    private final InterfaceType interfaceType;

    public MainGui(FAuction plugin, Player player, ViewType viewType, int page, InterfaceType interfaceType) {
        super(plugin, player, page);
        this.mainGuiConfig = plugin.getConfigurationManager().getMainGuiConfig();
        this.auctionCommandManager = plugin.getAuctionCommandManager();
        this.billCommandManager = plugin.getBillCommandManager();
        this.viewType = viewType;
        this.interfaceType = interfaceType;
        initGui(mainGuiConfig.getNameGui(), mainGuiConfig.getSize());
    }

    public void initializeItems() {

        if(interfaceType == InterfaceType.AUCTION) {
            TaskChain<ArrayList<Auction>> chain = null;
            if (viewType == ViewType.ALL) {
                chain = auctionCommandManager.getAuctions();
            } else if (viewType == ViewType.PLAYER) {
                chain = auctionCommandManager.getAuctions(player.getUniqueId());
            }
            TaskChain<ArrayList<Auction>> finalChain = chain;
            chain.sync(() -> {
                        this.auctions = finalChain.getTaskData("auctions");

                        String titleInv = mainGuiConfig.getNameGui();
                        titleInv = titleInv.replace("{Page}", String.valueOf(this.page));
                        titleInv = titleInv.replace("{TotalPage}", String.valueOf(((this.auctions.size() - 1) / mainGuiConfig.getItemBlocks().size()) + 1));

                        this.inv = Bukkit.createInventory(this, mainGuiConfig.getSize(), titleInv);

                        initBarrier();

                        if (this.auctions.isEmpty()) {
                            CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                            issuerTarget.sendInfo(MessageKeys.NO_AUCTION);
                            return;
                        }

                        int id = (this.mainGuiConfig.getItemBlocks().size() * this.page) - this.mainGuiConfig.getItemBlocks().size();
                        for (int index : mainGuiConfig.getItemBlocks()) {
                            inv.setItem(index, createGuiItem(auctions.get(id)));
                            id++;
                            if (id >= (auctions.size())) break;
                        }
                        openInventory(player);
                    }
            ).execute();
        } else if (interfaceType == InterfaceType.BILL) {
            TaskChain<ArrayList<Bill>> chain = null;
            if (viewType == ViewType.ALL) {
                chain = billCommandManager.getBills();
            } else if (viewType == ViewType.PLAYER) {
                chain = billCommandManager.getBills(player.getUniqueId());
            }
            TaskChain<ArrayList<Bill>> finalChain = chain;
            chain.sync(() -> {
                        this.bills = finalChain.getTaskData("bills");

                        String titleInv = mainGuiConfig.getNameGui();
                        titleInv = titleInv.replace("{Page}", String.valueOf(this.page));
                        titleInv = titleInv.replace("{TotalPage}", String.valueOf(((this.bills.size() - 1) / mainGuiConfig.getItemBlocks().size()) + 1));

                        this.inv = Bukkit.createInventory(this, mainGuiConfig.getSize(), titleInv);

                        initBarrier();

                        if (this.bills.isEmpty()) {
                            CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                            issuerTarget.sendInfo(MessageKeys.NO_BILL);
                            return;
                        }

                        int id = (this.mainGuiConfig.getItemBlocks().size() * this.page) - this.mainGuiConfig.getItemBlocks().size();
                        for (int index : mainGuiConfig.getItemBlocks()) {
                            inv.setItem(index, createGuiItem(bills.get(id)));
                            id++;
                            if (id >= (bills.size())) break;
                        }
                        openInventory(player);
                    }
            ).execute();
        }
    }

    private void initBarrier() {
        for(Barrier billView : mainGuiConfig.getBillBlocks()) {
            if(interfaceType == InterfaceType.AUCTION) {
                inv.setItem(billView.getIndex(), createGuiItem(billView.getMaterial(), billView.getTitle(), billView.getDescription()));
            } else if(interfaceType == InterfaceType.BILL) {
                inv.setItem(billView.getRemplacement().getIndex(), createGuiItem(billView.getRemplacement().getMaterial(), billView.getRemplacement().getTitle(), billView.getRemplacement().getDescription()));
            }
        }

        for (Barrier barrier : mainGuiConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
        }

        for (Barrier barrier : mainGuiConfig.getExpireBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
        }

        for (Barrier previous : mainGuiConfig.getPreviousBlocks()) {
            if (page > 1) {
                inv.setItem(previous.getIndex(), createGuiItem(previous.getMaterial(), previous.getTitle(), previous.getDescription()));
            } else {
                inv.setItem(previous.getRemplacement().getIndex(), createGuiItem(previous.getRemplacement().getMaterial(), previous.getRemplacement().getTitle(), previous.getRemplacement().getDescription()));
            }
        }

        for (Barrier next : mainGuiConfig.getNextBlocks()) {
            if ((this.mainGuiConfig.getItemBlocks().size() * this.page) - this.mainGuiConfig.getItemBlocks().size() < auctions.size() - this.mainGuiConfig.getItemBlocks().size()) {
                inv.setItem(next.getIndex(), createGuiItem(next.getMaterial(), next.getTitle(), next.getDescription()));
            } else {
                inv.setItem(next.getRemplacement().getIndex(), createGuiItem(next.getRemplacement().getMaterial(), next.getRemplacement().getTitle(), next.getRemplacement().getDescription()));
            }
        }

        for (Barrier player : mainGuiConfig.getPlayerBlocks()) {
            if (viewType == ViewType.ALL) {
                inv.setItem(player.getIndex(), createGuiItem(player.getMaterial(), player.getTitle(), player.getDescription()));
            } else if (viewType == ViewType.PLAYER) {
                inv.setItem(player.getRemplacement().getIndex(), createGuiItem(player.getRemplacement().getMaterial(), player.getRemplacement().getTitle(), player.getRemplacement().getDescription()));
            }
        }

        for (Barrier close : mainGuiConfig.getCloseBlocks()) {
            inv.setItem(close.getIndex(), createGuiItem(close.getMaterial(), close.getTitle(), close.getDescription()));
        }
    }

    private ItemStack createGuiItem(Auction auction) {
        ItemStack item = auction.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        String title = mainGuiConfig.getTitleAuction();
        if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            title = title.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
        } else {
            title = title.replace("{ItemName}", item.getItemMeta().getDisplayName());
        }
        title = title.replace("{ProprietaireName}", auction.getPlayerName());
        title = title.replace("{Price}", String.valueOf(auction.getPrice()));
        title = format(title);
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        List<String> listDescription = new ArrayList<>();

        for (String desc : mainGuiConfig.getDescriptionAuction()) {
            if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
            } else {
                desc = desc.replace("{ItemName}", item.getItemMeta().getDisplayName());
            }

            desc = desc.replace("{TotalVente}", String.valueOf(this.auctions.size()));
            desc = desc.replace("{ProprietaireName}", auction.getPlayerName());
            desc = desc.replace("{Price}", String.valueOf(auction.getPrice()));
            Date expireDate = new Date((auction.getDate().getTime() + globalConfig.getAuctionTime() * 1000L));
            SimpleDateFormat formater = new SimpleDateFormat("dd/MM/yyyy 'a' HH:mm");
            desc = desc.replace("{ExpireTime}", formater.format(expireDate));
            if (desc.contains("lore")) {
                if (item.getLore() != null) {
                    listDescription.addAll(item.getLore());
                } else {
                    listDescription.add(desc.replace("{lore}", ""));
                }
            } else {
                desc = format(desc);
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

    private ItemStack createGuiItem(Bill bill) {
        ItemStack item = bill.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        String title = mainGuiConfig.getTitleBill();
        if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            title = title.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
        } else {
            title = title.replace("{ItemName}", item.getItemMeta().getDisplayName());
        }
        title = title.replace("{ProprietaireName}", bill.getPlayerName());
        title = title.replace("{Price}", String.valueOf(bill.getPrice()));
        if(bill.getPlayerBidderName() != null) {
            title = title.replace("{BidderName}", bill.getPlayerBidderName());
        } else {
            title = title.replace("{BidderName}", "Personne");
        }

        title = format(title);
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        List<String> listDescription = new ArrayList<>();

        for (String desc : mainGuiConfig.getDescriptionBill()) {
            if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
            } else {
                desc = desc.replace("{ItemName}", item.getItemMeta().getDisplayName());
            }

            desc = desc.replace("{TotalVente}", String.valueOf(this.auctions.size()));
            desc = desc.replace("{ProprietaireName}", bill.getPlayerName());
            if(bill.getPlayerBidderName() != null) {
                desc = desc.replace("{BidderName}", bill.getPlayerBidderName());
            } else {
                desc = desc.replace("{BidderName}", "Personne");
            }

            desc = desc.replace("{Price}", String.valueOf(bill.getPrice()));
            Date expireDate = new Date((bill.getDate().getTime() + globalConfig.getAuctionTime() * 1000L));
            SimpleDateFormat formater = new SimpleDateFormat("dd/MM/yyyy 'a' HH:mm");
            desc = desc.replace("{ExpireTime}", formater.format(expireDate));
            if (desc.contains("lore")) {
                if (item.getLore() != null) {
                    listDescription.addAll(item.getLore());
                } else {
                    listDescription.add(desc.replace("{lore}", ""));
                }
            } else {
                desc = format(desc);
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
        name = format(name);
        List<String> descriptions = new ArrayList<>();
        for (String desc : description) {

            desc = desc.replace("{TotalVente}", String.valueOf(this.auctions.size()));
            desc = format(desc);
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

        for (Barrier previous : mainGuiConfig.getPreviousBlocks()) {
            if (e.getRawSlot() == previous.getIndex() && this.page > 1) {
                this.page = this.page - 1;
                initializeItems();
                return;
            }
        }
        for (Barrier next : mainGuiConfig.getNextBlocks()) {
            if (e.getRawSlot() == next.getIndex() && ((this.mainGuiConfig.getItemBlocks().size() * this.page) - this.mainGuiConfig.getItemBlocks().size() < auctions.size() - this.mainGuiConfig.getItemBlocks().size()) && next.getMaterial() != next.getRemplacement().getMaterial()) {
                this.page = this.page + 1;
                initializeItems();
                return;
            }
        }
        for (Barrier expire : mainGuiConfig.getExpireBlocks()) {
            if (e.getRawSlot() == expire.getIndex()) {
                ExpireGui gui = new ExpireGui(plugin, player, 1);
                gui.initializeItems();
                return;
            }
        }
        for (Barrier close : mainGuiConfig.getCloseBlocks()) {
            if (e.getRawSlot() == close.getIndex()) {
                inv.close();
                return;
            }
        }

        for (Barrier billBlocks : mainGuiConfig.getBillBlocks()) {
            if (e.getRawSlot() == billBlocks.getIndex()) {
                if(interfaceType == InterfaceType.AUCTION) {
                    MainGui mainGui = new MainGui(plugin, player, viewType, 1, InterfaceType.BILL);
                    mainGui.initializeItems();
                } else if(interfaceType == InterfaceType.BILL){
                    MainGui mainGui = new MainGui(plugin, player, viewType, 1, InterfaceType.AUCTION);
                    mainGui.initializeItems();
                }
                return;
            }
        }
        for (Barrier expire : mainGuiConfig.getPlayerBlocks()) {
            if (e.getRawSlot() == expire.getIndex()) {
                if(viewType == ViewType.ALL) {
                    MainGui gui = new MainGui(plugin, player, ViewType.PLAYER, 1, interfaceType);
                    gui.initializeItems();
                } else if (viewType == ViewType.PLAYER) {
                    MainGui gui = new MainGui(plugin, player, ViewType.ALL, 1, interfaceType);
                    gui.initializeItems();
                }
                return;
            }
        }

        for (int index : mainGuiConfig.getItemBlocks()) {
            if (index == e.getRawSlot()) {
                if (interfaceType == InterfaceType.AUCTION) {
                    int nb0 = mainGuiConfig.getItemBlocks().get(0);
                    int nb = (e.getRawSlot() - nb0) / 9;
                    Auction auction = auctions.get((e.getRawSlot() - nb0) + ((this.mainGuiConfig.getItemBlocks().size() * this.page) - this.mainGuiConfig.getItemBlocks().size()) - nb * 2);

                    if (e.isRightClick()) {
                        TaskChain<Auction> chainAuction = auctionCommandManager.auctionExist(auction.getId());
                        chainAuction.sync(() -> {
                            if (chainAuction.getTaskData("auction") == null) {
                                return;
                            }

                            if (!auction.getPlayerUuid().equals(player.getUniqueId())) {
                                return;
                            }

                            if (player.getInventory().firstEmpty() == -1) {
                                player.getWorld().dropItem(player.getLocation(), auction.getItemStack());
                            } else {
                                player.getInventory().addItem(auction.getItemStack());
                            }

                            auctionCommandManager.deleteAuction(auction.getId());
                            auctions.remove(auction);
                            CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                            issuerTarget.sendInfo(MessageKeys.REMOVE_AUCTION_SUCCESS);
                            inv.close();
                            MainGui gui = new MainGui(plugin, player, viewType, page, interfaceType);
                            gui.initializeItems();

                        }).execute();
                    } else if (e.isLeftClick()) {
                        CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                        if (auction.getPlayerUuid().equals(player.getUniqueId())) {
                            issuerTarget.sendInfo(MessageKeys.BUY_YOUR_ITEM);
                            return;
                        }
                        if (!plugin.getVaultIntegrationManager().getEconomy().has(player, auction.getPrice())) {
                            issuerTarget.sendInfo(MessageKeys.NO_HAVE_MONEY);
                            return;
                        }
                        ConfirmGui auctionConfirmGui = new ConfirmGui(plugin, player, page, auction, interfaceType);
                        auctionConfirmGui.initializeItems();
                    }
                } else if(interfaceType == InterfaceType.BILL) {
                    int nb0 = mainGuiConfig.getItemBlocks().get(0);
                    int nb = (e.getRawSlot() - nb0) / 9;
                    Bill bill = bills.get((e.getRawSlot() - nb0) + ((this.mainGuiConfig.getItemBlocks().size() * this.page) - this.mainGuiConfig.getItemBlocks().size()) - nb * 2);

                    if (e.isRightClick()) {
                        TaskChain<Bill> chainAuction = billCommandManager.billExist(bill.getId());
                        chainAuction.sync(() -> {
                            if (chainAuction.getTaskData("bill") == null) {
                                return;
                            }

                            if (!bill.getPlayerUuid().equals(player.getUniqueId())) {
                                return;
                            }

                            if (player.getInventory().firstEmpty() == -1) {
                                player.getWorld().dropItem(player.getLocation(), bill.getItemStack());
                            } else {
                                player.getInventory().addItem(bill.getItemStack());
                            }

                            billCommandManager.deleteBill(bill.getId());
                            bills.remove(bill);
                            CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                            issuerTarget.sendInfo(MessageKeys.REMOVE_BILL_SUCCESS);
                            inv.close();
                            MainGui gui = new MainGui(plugin, player, viewType, page, interfaceType);
                            gui.initializeItems();

                        }).execute();
                    } else if (e.isLeftClick()) {
                        CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                        if (bill.getPlayerUuid().equals(player.getUniqueId())) {
                            issuerTarget.sendInfo(MessageKeys.BUY_YOUR_ITEM);
                            return;
                        }
                        if (!plugin.getVaultIntegrationManager().getEconomy().has(player, bill.getPrice())) {
                            issuerTarget.sendInfo(MessageKeys.NO_HAVE_MONEY);
                            return;
                        }
                        ConfirmGui confirmGui = new ConfirmGui(plugin, player, page, bill, interfaceType);
                        confirmGui.initializeItems();
                    }
                }
                break;
            }
        }
    }

    private String format(String msg) {
        Pattern pattern = Pattern.compile("[{]#[a-fA-F0-9]{6}[}]");
        if (Bukkit.getVersion().contains("1.16")) {

            Matcher match = pattern.matcher(msg);
            while (match.find()) {
                String color = msg.substring(match.start(), match.end());
                String replace = color;
                color = color.replace("{", "");
                color = color.replace("}", "");
                msg = msg.replace(replace, net.md_5.bungee.api.ChatColor.of(color) + "");
                match = pattern.matcher(msg);
            }
        }
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', msg);
    }
}