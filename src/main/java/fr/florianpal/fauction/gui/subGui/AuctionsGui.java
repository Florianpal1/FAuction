package fr.florianpal.fauction.gui.subGui;

import co.aikar.commands.CommandIssuer;
import co.aikar.taskchain.TaskChain;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.gui.AuctionConfig;
import fr.florianpal.fauction.gui.AbstractGuiWithAuctions;
import fr.florianpal.fauction.gui.GuiInterface;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Barrier;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.utils.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AuctionsGui extends AbstractGuiWithAuctions implements GuiInterface {

    private final AuctionConfig auctionConfig;

    private final List<LocalDateTime> spamTest = new ArrayList<>();

    public AuctionsGui(FAuction plugin, Player player, List<Auction> auctions, int page, Category category) {
        super(plugin, player, page, auctions, plugin.getConfigurationManager().getAuctionConfig());
        this.auctionConfig = plugin.getConfigurationManager().getAuctionConfig();

        if (category == null) category = plugin.getConfigurationManager().getCategoriesConfig().getDefault();
        this.category = category;

        if (!category.containsAll()) {
            this.auctions = auctions.stream().filter(a -> this.category.getMaterials().contains(a.getItemStack().getType())).toList();
        }

        initGui(auctionConfig.getNameGui(), auctionConfig.getSize());
    }

    public void initializeItems() {

        initBarrier();

        if (!auctions.isEmpty()) {
            int id = (this.auctionConfig.getAuctionBlocks().size() * this.page) - this.auctionConfig.getAuctionBlocks().size();
            for (int index : auctionConfig.getAuctionBlocks()) {
                inv.setItem(index, createGuiItem(auctions.get(id)));
                id++;
                if (id >= (auctions.size())) break;
            }
        }
        openInventory(player);
    }

    private void initBarrier() {

        for (Barrier barrier : auctionConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(getItemStack(barrier, false)));
        }

        for (Barrier barrier : auctionConfig.getExpireBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(getItemStack(barrier, false)));
        }

        for (Barrier previous : auctionConfig.getPreviousBlocks()) {
            if (page > 1) {
                inv.setItem(previous.getIndex(), createGuiItem(getItemStack(previous, false)));
            } else {
                inv.setItem(previous.getRemplacement().getIndex(), createGuiItem(getItemStack(previous, true)));
            }
        }

        for (Barrier next : auctionConfig.getNextBlocks()) {
            if ((this.auctionConfig.getAuctionBlocks().size() * this.page) - this.auctionConfig.getAuctionBlocks().size() < auctions.size() - this.auctionConfig.getAuctionBlocks().size()) {
                inv.setItem(next.getIndex(), createGuiItem(getItemStack(next, false)));
            } else {
                inv.setItem(next.getRemplacement().getIndex(), createGuiItem(getItemStack(next, true)));
            }
        }

        for (Barrier player : auctionConfig.getPlayerBlocks()) {
            inv.setItem(player.getIndex(), createGuiItem(getItemStack(player, false)));
        }

        for (Barrier close : auctionConfig.getCloseBlocks()) {
            inv.setItem(close.getIndex(), createGuiItem(getItemStack(close, false)));
        }

        for (Barrier categoryBlock : auctionConfig.getCategoriesBlocks()) {
            inv.setItem(categoryBlock.getIndex(), createGuiItem(getItemStack(categoryBlock, false)));
        }
    }

    private ItemStack createGuiItem(Auction auction) {
        ItemStack item = auction.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        String title = auctionConfig.getTitle();
        if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            title = title.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
        } else {
            title = title.replace("{ItemName}", item.getItemMeta().getDisplayName());
        }
        title = title.replace("{OwnerName}", auction.getPlayerName());
        title = title.replace("{Price}", String.valueOf(auction.getPrice()));

        var offlinePlayer = Bukkit.getOfflinePlayer(auction.getPlayerUuid());
        if (offlinePlayer != null) {
            title = plugin.parsePlaceholder(offlinePlayer, title);
        }
        
        title = FormatUtil.format(title);
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        List<String> listDescription = new ArrayList<>();

        for (String desc : auctionConfig.getDescription()) {
            if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
            } else {
                desc = desc.replace("{ItemName}", item.getItemMeta().getDisplayName());
            }

            desc = desc.replace("{TotalSale}", String.valueOf(this.auctions.size()));
            desc = desc.replace("{OwnerName}", auction.getPlayerName());

            if (offlinePlayer != null) {
                desc = plugin.parsePlaceholder(offlinePlayer, desc);
            }

            desc = desc.replace("{Price}", String.valueOf(auction.getPrice()));
            Date expireDate = new Date((auction.getDate().getTime() + globalConfig.getTime() * 1000L));
            SimpleDateFormat formater = new SimpleDateFormat(globalConfig.getDateFormat());
            desc = desc.replace("{ExpireTime}", formater.format(expireDate));
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
            if (auctionConfig.isReplaceTitle()) {
                meta.setDisplayName(title);
            }
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

            desc = desc.replace("{TotalSale}", String.valueOf(this.auctions.size()));
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

    public ItemStack createGuiItem(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || meta.getDisplayName() == null || meta.getLore() == null) {
            return itemStack;
        }
        String name = FormatUtil.format(meta.getDisplayName());
        List<String> descriptions = new ArrayList<>();
        for (String desc : meta.getLore()) {

            desc = desc.replace("{TotalSale}", String.valueOf(this.auctions.size()));
            desc = FormatUtil.format(desc);
            descriptions.add(desc);
        }
        meta.setDisplayName(name);
        meta.setLore(descriptions);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory() != inv || inv.getHolder() != this || player != e.getWhoClicked()) {
            return;
        }
        e.setCancelled(true);

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        for (Barrier previous : auctionConfig.getPreviousBlocks()) {
            if (e.getRawSlot() == previous.getIndex() && this.page > 1) {

                FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                    AuctionsGui gui = new AuctionsGui(plugin, player, auctions, this.page - 1, category);
                    gui.initializeItems();
                }).execute();
            }
        }
        for (Barrier next : auctionConfig.getNextBlocks()) {
            if (e.getRawSlot() == next.getIndex() && ((this.auctionConfig.getAuctionBlocks().size() * this.page) - this.auctionConfig.getAuctionBlocks().size() < auctions.size() - this.auctionConfig.getAuctionBlocks().size()) && next.getMaterial() != next.getRemplacement().getMaterial()) {

                FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                    AuctionsGui gui = new AuctionsGui(plugin, player, auctions, this.page + 1, category);
                    gui.initializeItems();
                }).execute();
                return;
            }
        }
        for (Barrier expire : auctionConfig.getExpireBlocks()) {
            if (e.getRawSlot() == expire.getIndex()) {

                FAuction.newChain().asyncFirst(() -> expireCommandManager.getAuctions(player.getUniqueId())).syncLast(auctions -> {
                    ExpireGui gui = new ExpireGui(plugin, player, auctions, 1);
                    gui.initializeItems();
                }).execute();
                return;
            }
        }

        for (Barrier categoryB : auctionConfig.getCategoriesBlocks()) {
            if (e.getRawSlot() == categoryB.getIndex()) {

                Category nextCategory = plugin.getConfigurationManager().getCategoriesConfig().getNext(category);

                FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                    AuctionsGui gui = new AuctionsGui(plugin, player, auctions, 1, nextCategory);
                    gui.initializeItems();
                }).execute();

                return;
            }
        }

        for (Barrier close : auctionConfig.getCloseBlocks()) {
            if (e.getRawSlot() == close.getIndex()) {
                player.closeInventory();
                return;
            }
        }

        for (Barrier expire : auctionConfig.getPlayerBlocks()) {
            if (e.getRawSlot() == expire.getIndex()) {

                FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                    PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions, 1);
                    gui.initializeItems();
                }).execute();

                return;
            }
        }

        if (globalConfig.isSecurityForSpammingPacket()) {
            LocalDateTime clickTest = LocalDateTime.now();
            boolean isSpamming = spamTest.stream().anyMatch(d -> d.getHour() == clickTest.getHour() && d.getMinute() == clickTest.getMinute() && (d.getSecond() == clickTest.getSecond() || d.getSecond() == clickTest.getSecond() + 1 || d.getSecond() == clickTest.getSecond() - 1));
            if (isSpamming) {
                plugin.getLogger().warning("Warning : Spam gui auction Pseudo : " + player.getName());
                CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                issuerTarget.sendInfo(MessageKeys.SPAM);
                return;
            } else {
                spamTest.add(clickTest);
            }
        }

        for (int index : auctionConfig.getAuctionBlocks()) {
            if (index == e.getRawSlot()) {
                int nb0 = auctionConfig.getAuctionBlocks().get(0);
                int nb = ((e.getRawSlot() - nb0)) / 9;
                Auction auction = auctions.get((e.getRawSlot() - nb0) + ((this.auctionConfig.getAuctionBlocks().size() * this.page) - this.auctionConfig.getAuctionBlocks().size()) - nb * 2);

                if (e.isRightClick()) {
                    TaskChain<Auction> chainAuction = FAuction.newChain();
                    chainAuction.asyncFirst(() -> auctionCommandManager.auctionExist(auction.getId())).syncLast(a -> {

                        if (a == null) {
                            return;
                        }

                        boolean isModCanCancel = (e.isShiftClick() && player.hasPermission("fauction.mod.cancel"));
                        if (!a.getPlayerUuid().equals(player.getUniqueId()) && !isModCanCancel) {
                            return;
                        }

                        if (!isModCanCancel) {
                            if (player.getInventory().firstEmpty() == -1) {
                                player.getWorld().dropItem(player.getLocation(), a.getItemStack());
                            } else {
                                player.getInventory().addItem(a.getItemStack());
                            }
                        }

                        auctionCommandManager.deleteAuction(a.getId());
                        if (isModCanCancel) {
                            plugin.getExpireCommandManager().addAuction(a);
                            plugin.getLogger().info("Modo delete from ah auction : " + a.getId() + ", Item : " + a.getItemStack().getItemMeta().getDisplayName() + " of " + a.getPlayerName() + ", by" + player.getName());
                        } else {
                            plugin.getLogger().info("Player delete from ah auction : " + a.getId() + ", Item : " + a.getItemStack().getItemMeta().getDisplayName() + " of " + a.getPlayerName() + ", by" + player.getName());
                        }
                        auctions.remove(a);
                        CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                        issuerTarget.sendInfo(MessageKeys.REMOVE_AUCTION_SUCCESS);

                        player.closeInventory();

                        TaskChain<ArrayList<Auction>> chain = FAuction.newChain();
                        chain.asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                            AuctionsGui gui = new AuctionsGui(plugin, player, auctions, 1, category);
                            gui.initializeItems();
                        }).execute();
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
}