
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
import fr.florianpal.fauction.configurations.AuctionConfig;
import fr.florianpal.fauction.gui.AbstractGui;
import fr.florianpal.fauction.gui.GuiInterface;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.commandManagers.AuctionCommandManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Barrier;
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

public class AuctionsGui extends AbstractGui implements GuiInterface {
    private List<Auction> auctions = new ArrayList<>();
    protected final AuctionCommandManager auctionCommandManager;
    private final AuctionConfig auctionConfig;

    public AuctionsGui(FAuction plugin, Player player, int page) {
        super(plugin, player, page);
        this.auctionConfig = plugin.getConfigurationManager().getAuctionConfig();
        this.auctionCommandManager = new AuctionCommandManager(plugin);
        initGui(auctionConfig.getNameGui(), 27);
    }

    public void initializeItems() {
        TaskChain<ArrayList<Auction>> chain = auctionCommandManager.getAuctions();
        chain.sync(() -> {
                    this.auctions = chain.getTaskData("auctions");

                    String titleInv = auctionConfig.getNameGui();
                    titleInv = titleInv.replace("{Page}", String.valueOf(this.page));
                    titleInv = titleInv.replace("{TotalPage}", String.valueOf(((this.auctions.size() - 1) / auctionConfig.getAuctionBlocks().size()) + 1));

                    this.inv = Bukkit.createInventory(this, auctionConfig.getSize(), titleInv);

                    if (this.auctions.size() == 0) {
                        CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                        issuerTarget.sendInfo(MessageKeys.NO_AUCTION);
                        return;
                    }
                    for (Barrier barrier : auctionConfig.getBarrierBlocks()) {
                        inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
                    }

                    for (Barrier barrier : auctionConfig.getExpireBlocks()) {
                        inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
                    }

                    for (Barrier previous : auctionConfig.getPreviousBlocks()) {
                        if (page > 1) {
                            inv.setItem(previous.getIndex(), createGuiItem(previous.getMaterial(), previous.getTitle(), previous.getDescription()));

                        } else {
                            inv.setItem(previous.getRemplacement().getIndex(), createGuiItem(previous.getRemplacement().getMaterial(), previous.getRemplacement().getTitle(), previous.getRemplacement().getDescription()));
                        }
                    }

                    for (Barrier next : auctionConfig.getNextBlocks()) {
                        if ((this.auctionConfig.getAuctionBlocks().size() * this.page) - this.auctionConfig.getAuctionBlocks().size() < auctions.size() - this.auctionConfig.getAuctionBlocks().size()) {
                            inv.setItem(next.getIndex(), createGuiItem(next.getMaterial(), next.getTitle(), next.getDescription()));
                        } else {
                            inv.setItem(next.getRemplacement().getIndex(), createGuiItem(next.getRemplacement().getMaterial(), next.getRemplacement().getTitle(), next.getRemplacement().getDescription()));
                        }
                    }
                     for (Barrier close : auctionConfig.getCloseBlocks()) {
                         inv.setItem(close.getIndex(), createGuiItem(close.getMaterial(), close.getTitle(), close.getDescription()));
                    }




                    int id = (this.auctionConfig.getAuctionBlocks().size() * this.page) - this.auctionConfig.getAuctionBlocks().size();
                    for (int index : auctionConfig.getAuctionBlocks()) {
                        String ownerName = this.auctions.get(id).getPlayerName();
                        inv.setItem(index, createGuiItem(auctions.get(id), ownerName));
                        id++;
                        if (id >= (auctions.size())) break;
                    }
                    openInventory(player);
                }
        ).execute();
    }

    private ItemStack createGuiItem(Auction auction, String playerName) {
        ItemStack item = auction.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        String title = auctionConfig.getTitle();
        if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            title = title.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
        } else {
            title = title.replace("{ItemName}", item.getItemMeta().getDisplayName());
        }
        title = title.replace("{ProprietaireName}", playerName);
        title = title.replace("{Price}", String.valueOf(auction.getPrice()));
        title = format(title);
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        List<String> listDescription = new ArrayList<>();

        for (String desc : auctionConfig.getDescription()) {
            if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
            } else {
                desc = desc.replace("{ItemName}", item.getItemMeta().getDisplayName());
            }

            desc = desc.replace("{TotalVente}", String.valueOf(this.auctions.size()));
            desc = desc.replace("{ProprietaireName}", playerName);
            desc = desc.replace("{Price}", String.valueOf(auction.getPrice()));
            Date expireDate = new Date((auction.getDate().getTime() + globalConfig.getTime() * 1000L));
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
        if (inv.getHolder() != this) {
            return;
        }
        if (!(e.getInventory() == inv)) {
            return;
        }
        e.setCancelled(true);
        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        for (Barrier previous : auctionConfig.getPreviousBlocks()) {
            if (e.getRawSlot() == previous.getIndex() && this.page > 1) {
                AuctionsGui gui = new AuctionsGui(plugin, player, this.page - 1);
                gui.initializeItems();
                return;
            }
        }
        for (Barrier next : auctionConfig.getNextBlocks()) {
            if (e.getRawSlot() == next.getIndex() && ((this.auctionConfig.getAuctionBlocks().size() * this.page) - this.auctionConfig.getAuctionBlocks().size() < auctions.size() - this.auctionConfig.getAuctionBlocks().size()) && next.getMaterial() != next.getRemplacement().getMaterial()) {
                AuctionsGui gui = new AuctionsGui(plugin, player, this.page + 1);
                gui.initializeItems();
                return;
            }
        }
        for (Barrier expire : auctionConfig.getExpireBlocks()) {
            if (e.getRawSlot() == expire.getIndex()) {
                ExpireGui gui = new ExpireGui(plugin, player, 1);
                gui.initializeItems();
                return;
            }
        }
        for (Barrier close : auctionConfig.getCloseBlocks()) {
            if (e.getRawSlot() == close.getIndex()) {
                inv.close();
                return;
            }
        }

        for (int index : auctionConfig.getAuctionBlocks()) {
            if (index == e.getRawSlot()) {
                int nb0 = auctionConfig.getAuctionBlocks().get(0);
                int nb = ((e.getRawSlot() - nb0)) / 9;
                Auction auction = auctions.get((e.getRawSlot() - nb0) + ((this.auctionConfig.getAuctionBlocks().size() * this.page) - this.auctionConfig.getAuctionBlocks().size()) - nb * 2);

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
                        AuctionsGui gui = new AuctionsGui(plugin, player, page);
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
                    AuctionConfirmGui auctionConfirmGui = new AuctionConfirmGui(plugin, player, page, auction);
                    auctionConfirmGui.initializeItems();
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