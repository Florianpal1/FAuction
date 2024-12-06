package fr.florianpal.fauction.gui.subGui;

import co.aikar.commands.CommandIssuer;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.gui.AuctionConfirmGuiConfig;
import fr.florianpal.fauction.gui.AbstractGui;
import fr.florianpal.fauction.gui.GuiInterface;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Barrier;
import fr.florianpal.fauction.objects.Confirm;
import fr.florianpal.fauction.utils.FormatUtil;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bukkit.Bukkit.getServer;

public class AuctionConfirmGui extends AbstractGui implements GuiInterface {

    private final Auction auction;
    protected final AuctionConfirmGuiConfig auctionConfirmConfig;

    private final Map<Integer, Confirm> confirmList = new HashMap<>();

    private final List<LocalDateTime> spamTest = new ArrayList<>();

    AuctionConfirmGui(FAuction plugin, Player player, int page, Auction auction) {
        super(plugin, player, page, plugin.getConfigurationManager().getAuctionConfirmConfig());
        this.auction = auction;
        this.auctionConfirmConfig = plugin.getConfigurationManager().getAuctionConfirmConfig();
        initGui(auctionConfirmConfig.getNameGui(), auctionConfirmConfig.getSize());
    }

    public void initializeItems() {

        for (Barrier barrier : auctionConfirmConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), getItemStack(barrier, false));
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
        title = title.replace("{OwnerName}", confirm.getAuction().getPlayerName());

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(auction.getPlayerUUID());
        if (offlinePlayer != null) {
            title = plugin.parsePlaceholder(offlinePlayer, title);
        }

        title = FormatUtil.format(title);
        List<String> listDescription = new ArrayList<>();
        for (String desc : auctionConfirmConfig.getDescription()) {
            desc = desc.replace("{Price}", df.format(confirm.getAuction().getPrice()));
            if (confirm.getAuction().getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{Item}", confirm.getAuction().getItemStack().getType().toString());
            } else {
                desc = desc.replace("{Item}", confirm.getAuction().getItemStack().getItemMeta().getDisplayName());
            }
            desc = desc.replace("{OwnerName}", confirm.getAuction().getPlayerName());

            if (offlinePlayer != null) {
                desc = plugin.parsePlaceholder(offlinePlayer, desc);
            }

            desc = FormatUtil.format(desc);
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
        if (e.getInventory() != inv || inv.getHolder() != this || player != e.getWhoClicked()) {
            return;
        }
        e.setCancelled(true);

        if (globalConfig.isSecurityForSpammingPacket()) {
            LocalDateTime clickTest = LocalDateTime.now();
            boolean isSpamming = spamTest.stream().anyMatch(d -> d.getHour() == clickTest.getHour() && d.getMinute() == clickTest.getMinute() && (d.getSecond() == clickTest.getSecond() || d.getSecond() == clickTest.getSecond() + 1 || d.getSecond() == clickTest.getSecond() - 1));
            if (isSpamming) {
                plugin.getLogger().warning("Warning : Spam gui auction confirm Pseudo : " + player.getName());
                CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                issuerTarget.sendInfo(MessageKeys.SPAM);
                return;
            } else {
                spamTest.add(clickTest);
            }
        }

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        for (Map.Entry<Integer, Confirm> entry : auctionConfirmConfig.getConfirmBlocks().entrySet()) {
            if (entry.getKey() == e.getRawSlot()) {
                CommandIssuer issuerTarget = commandManager.getCommandIssuer(player);
                Confirm confirm = confirmList.get(e.getRawSlot());
                if (!confirm.isValue()) {
                    issuerTarget.sendInfo(MessageKeys.BUY_AUCTION_CANCELLED);
                    player.getOpenInventory().close();

                    FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                        AuctionsGui gui = new AuctionsGui(plugin, player, auctions, 1, null);
                        gui.initializeItems();
                    }).execute();
                    return;
                }

                FAuction.newChain().asyncFirst(() -> auctionCommandManager.auctionExist(this.auction.getId())).syncLast(a -> {
                    if (a == null) {
                        issuerTarget.sendInfo(MessageKeys.NO_AUCTION);
                        return;
                    }

                    FAuction.newChain().asyncFirst(() -> auctionCommandManager.auctionExist(this.auction.getId())).syncLast(auctionGood -> {
                        if (auctionGood == null) {
                            issuerTarget.sendInfo(MessageKeys.AUCTION_ALREADY_SELL);
                            return;
                        }

                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(auctionGood.getPlayerUUID());
                        if (offlinePlayer == null) {
                            return;
                        }

                        EconomyResponse economyResponse4 = plugin.getVaultIntegrationManager().getEconomy().depositPlayer(offlinePlayer, auctionGood.getPrice());
                        if (!economyResponse4.transactionSuccess()) {
                            return;
                        }

                        EconomyResponse economyResponse5 = plugin.getVaultIntegrationManager().getEconomy().withdrawPlayer(player, auctionGood.getPrice());
                        if (!economyResponse5.transactionSuccess()) {
                            return;
                        }

                        issuerTarget.sendInfo(MessageKeys.BUY_AUCTION_SUCCESS);
                        auctionCommandManager.deleteAuction(auctionGood.getId());
                        historicCommandManager.addHistoric(a, player.getUniqueId(), player.getName());

                        if (player.getInventory().firstEmpty() == -1) {
                            player.getWorld().dropItem(player.getLocation(), auctionGood.getItemStack());
                        } else {
                            player.getInventory().addItem(auctionGood.getItemStack());
                        }

                        if (plugin.getConfigurationManager().getGlobalConfig().isOnBuyCommandUse()) {
                            String command = getCommand(auctionGood);
                            getServer().dispatchCommand(getServer().getConsoleSender(), command);
                        }

                        plugin.getLogger().info("Player : " + player.getName() + " buy " + auctionGood.getItemStack().getItemMeta().getDisplayName() + " at " + auctionGood.getPlayerName());

                        player.getOpenInventory().close();
                        FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                            AuctionsGui gui = new AuctionsGui(plugin, player, auctions, 1, null);
                            gui.initializeItems();
                        }).execute();
                    }).execute();
                }).execute();
                break;
            }
        }
    }

    @NotNull
    private String getCommand(Auction auctionGood) {
        String command = plugin.getConfigurationManager().getGlobalConfig().getOnBuyCommand();
        command = command.replace("{OwnerName}", auctionGood.getPlayerName());
        command = command.replace("{Amount}", String.valueOf(auctionGood.getItemStack().getAmount()));
        if (!auctionGood.getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            command = command.replace("{ItemName}", auctionGood.getItemStack().getItemMeta().getDisplayName());
        } else {
            command = command.replace("{ItemName}", auctionGood.getItemStack().getType().name().replace('_', ' ').toLowerCase());
        }
        command = command.replace("{BuyerName}", player.getName());
        command = command.replace("{ItemPrice}", String.valueOf(auctionGood.getPrice()));
        return command;
    }
}
