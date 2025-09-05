package fr.florianpal.fauction.gui.subGui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.gui.AuctionConfirmGuiConfig;
import fr.florianpal.fauction.enums.Gui;
import fr.florianpal.fauction.events.AuctionBuyEvent;
import fr.florianpal.fauction.gui.AbstractGuiWithAuctions;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.objects.*;
import fr.florianpal.fauction.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;

import static org.bukkit.Bukkit.getServer;

public class AuctionConfirmGui extends AbstractGuiWithAuctions {

    private final Auction auction;

    protected final AuctionConfirmGuiConfig auctionConfirmConfig;

    private final Map<Integer, Confirm> confirmList = new HashMap<>();

    public AuctionConfirmGui(FAuction plugin, Player player, int page, Auction auction) {
        super(plugin, player, page, Collections.singletonList(auction), null, null, plugin.getConfigurationManager().getAuctionConfirmConfig());
        this.auction = auction;
        this.auctionConfirmConfig = plugin.getConfigurationManager().getAuctionConfirmConfig();
    }

    public void initialize() {

        initGui(abstractGuiConfig.getNameGui(), abstractGuiConfig.getSize());
        initBarrier();

        for (Barrier barrier : auctionConfirmConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), getItemStack(barrier, false));
        }

        for (Integer index : auctionConfirmConfig.getBaseBlocks()) {
            inv.setItem(index, createGuiItem(auction));
        }

        int id = 0;
        for (Confirm entry : auctionConfirmConfig.getConfirmBlocks()) {
            Confirm confirm = new Confirm(entry.getIndex(), this.auction,
                    entry.getMaterial(),
                    entry.isValue(),
                    entry.getTexture(),
                    entry.getCustomModelData()
            );
            confirmList.put(entry.getIndex(), confirm);
            inv.setItem(entry.getIndex(), createGuiItem(confirm));
            id++;
            if (id >= (auctionConfirmConfig.getConfirmBlocks().size())) break;
        }
        openInventory(player);
    }

    public ItemStack createGuiItem(Auction auction) {
        ItemStack item = auction.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        String title = auctionConfirmConfig.getAuctionTitle();
        if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            title = title.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
        } else {
            title = title.replace("{ItemName}", item.getItemMeta().getDisplayName());
        }
        title = title.replace("{OwnerName}", auction.getPlayerName());
        title = title.replace("{Price}", df.format(auction.getPrice()));

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(auction.getPlayerUUID());
        if (offlinePlayer != null) {
            title = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, title);
        }

        title = FormatUtil.format(title);
        List<String> listDescription = new ArrayList<>();

        for (String desc : auctionConfirmConfig.getAuctionDescription()) {
            if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
            } else {
                desc = desc.replace("{ItemName}", item.getItemMeta().getDisplayName());
            }

            desc = desc.replace("{OwnerName}", auction.getPlayerName());

            if (auction instanceof Historic) {
                Historic historic = (Historic) auction;
                desc = desc.replace("{BuyerName}", historic.getPlayerBuyerName());
            }

            if (offlinePlayer != null) {
                desc = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, desc);
            }

            desc = desc.replace("{Price}", df.format(auction.getPrice()));
            Date expireDate = new Date((auction.getDate().getTime() + globalConfig.getTime() * 1000L));
            desc = desc.replace("{ExpireTime}", dateFormater.format(expireDate));

            Duration duration = Duration.between(new Date().toInstant(), new Date(auction.getDate().getTime() + globalConfig.getTime() * 1000L).toInstant());
            desc = desc.replace("{RemainingTime}", FormatUtil.durationFormat(globalConfig.getRemainingDateFormat(), duration));
            if (desc.contains("lore")) {
                if (item.getItemMeta().getLore() != null) {
                    listDescription.addAll(item.getItemMeta().getLore());
                } else {
                    listDescription.add(desc.replace("{lore}", ""));
                }
            } else {
                desc = FormatUtil.format(desc);
                listDescription.add(desc);
            }
        }
        if (meta != null) {
            if (auctionConfirmConfig.isReplaceTitle()) {
                meta.setDisplayName(title);
            }
            meta.setLore(listDescription);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    protected void previousAction() {

    }

    @Override
    protected void nextAction() {

    }

    @Override
    protected void categoryAction(Category nextCategory) {

    }

    @Override
    protected void sortingAction(Sort nextSort) {
    }

    private ItemStack createGuiItem(Confirm confirm) {
        ItemStack item = new ItemStack(confirm.getMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        String title;
        if (confirm.isValue()) {
            title = auctionConfirmConfig.getTitleTrue();
        } else {
            title = auctionConfirmConfig.getTitleFalse();
        }

        ItemStack itemStack = confirm.getAuction().getItemStack().clone();
        title = FormatUtil.titleItemFormat(confirm.getAuction().getItemStack(), "{Item}", title);

        title = title.replace("{Price}", df.format(confirm.getAuction().getPrice()));
        title = title.replace("{OwnerName}", confirm.getAuction().getPlayerName());

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(auction.getPlayerUUID());
        if (offlinePlayer != null) {
            title = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, title);
        }

        title = FormatUtil.format(title);
        List<String> listDescription = new ArrayList<>();
        for (String desc : auctionConfirmConfig.getDescription()) {
            desc = desc.replace("{Price}", df.format(confirm.getAuction().getPrice()));
            desc = FormatUtil.titleItemFormat(confirm.getAuction().getItemStack(), "{Item}", desc);
            desc = desc.replace("{OwnerName}", confirm.getAuction().getPlayerName());

            if (offlinePlayer != null) {
                desc = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, desc);
            }

            desc = FormatUtil.format(desc);
            listDescription.add(desc);
        }

        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(listDescription);
            meta.setCustomModelData(confirm.getCustomModelData());
            item.setItemMeta(meta);
        }
        if (confirm.getMaterial() == Material.PLAYER_HEAD) {
            PlayerHeadUtil.addTexture(itemStack, confirm.getTexture());
            itemStack.setAmount(1);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {

        if (e.getInventory() != inv || inv.getHolder() != this || player != e.getWhoClicked()) {
            return;
        }
        e.setCancelled(true);

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if(guiClick(e)) {
            return;
        }

        if (spamManager.spamTest(player)) {
            return;
        }

        boolean isBaseBlock = abstractGuiWithAuctionsConfig.getBaseBlocks().stream().anyMatch(b -> b == e.getRawSlot());
        if (isBaseBlock) {

            VisualizationUtils.createVizualisation(plugin, auction, player, Gui.CONFIRM);
            return;
        }

        for (Confirm entry : auctionConfirmConfig.getConfirmBlocks()) {
            if (entry.getIndex() == e.getRawSlot()) {

                Confirm confirm = confirmList.get(e.getRawSlot());
                if (!confirm.isValue()) {
                    MessageUtil.sendMessage(plugin, player, MessageKeys.BUY_AUCTION_CANCELLED);
                    FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                        AuctionsGui gui = new AuctionsGui(plugin, player, auctions, 1, null, null);
                        gui.initialize();
                    }).execute();
                    return;
                }

                FAuction.newChain().asyncFirst(() -> auctionCommandManager.auctionExist(this.auction.getId())).syncLast(a -> {
                    if (a == null) {
                        MessageUtil.sendMessage(plugin, player, MessageKeys.NO_AUCTION);
                        return;
                    }

                    FAuction.newChain().asyncFirst(() -> auctionCommandManager.auctionExist(this.auction.getId())).syncLast(auctionGood -> {
                        if (auctionGood == null) {
                            MessageUtil.sendMessage(plugin, player, MessageKeys.AUCTION_ALREADY_SELL);
                            return;
                        }

                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(auctionGood.getPlayerUUID());
                        if (offlinePlayer == null) {
                            return;
                        }

                        if (!CurrencyUtil.haveCurrency(plugin, player, globalConfig.getCurrencyType(), auctionGood.getPrice())) {
                            MessageUtil.sendMessage(plugin, player, MessageKeys.NO_HAVE_MONEY);
                            return;
                        }

                        if (!CurrencyUtil.getCurrency(plugin, player, globalConfig.getCurrencyType(), auctionGood.getPrice())) {
                            return;
                        }

                        if (!CurrencyUtil.giveCurrency(plugin, offlinePlayer, globalConfig.getCurrencyType(), auctionGood.getPrice())) {
                            return;
                        }

                        MessageUtil.sendMessage(plugin, player, MessageKeys.BUY_AUCTION_SUCCESS);

                        if (offlinePlayer.isOnline()) {
                            MessageUtil.sendMessage(plugin, offlinePlayer.getPlayer(), MessageKeys.BUY_AUCTION_TARGET_SUCCESS, "{player}", player.getName(), "{item}", FormatUtil.titleItemFormat(auctionGood.getItemStack()), "{price}", String.valueOf(auctionGood.getPrice()));
                        }

                        auctionCommandManager.deleteAuction(auctionGood.getId());
                        historicCommandManager.addHistoric(a, player.getUniqueId(), player.getName());

                        if (player.getInventory().firstEmpty() == -1) {
                            player.getWorld().dropItem(player.getLocation(), auctionGood.getItemStack());
                        } else {
                            player.getInventory().addItem(auctionGood.getItemStack());
                        }

                        Bukkit.getPluginManager().callEvent(new AuctionBuyEvent(player, a));

                        if (plugin.getConfigurationManager().getGlobalConfig().isOnBuyCommandUse()) {
                            String command = getCommand(auctionGood);
                            getServer().dispatchCommand(getServer().getConsoleSender(), command);
                        }

                        plugin.getLogger().info("Player : " + player.getName() + " buy " + auctionGood.getItemStack().getItemMeta().getDisplayName() + " at " + auctionGood.getPlayerName());

                        FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                            AuctionsGui gui = new AuctionsGui(plugin, player, auctions, 1, null, null);
                            gui.initialize();
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
        command = command.replace("{ItemPrice}", df.format(auctionGood.getPrice()));
        return command;
    }
}
