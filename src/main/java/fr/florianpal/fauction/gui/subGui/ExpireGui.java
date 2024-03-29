package fr.florianpal.fauction.gui.subGui;

import co.aikar.commands.CommandIssuer;
import co.aikar.taskchain.TaskChain;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.ExpireGuiConfig;
import fr.florianpal.fauction.enums.ViewType;
import fr.florianpal.fauction.gui.AbstractGui;
import fr.florianpal.fauction.gui.GuiInterface;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.commandManagers.ExpireCommandManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Barrier;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpireGui extends AbstractGui implements GuiInterface {
    private List<Auction> auctions = new ArrayList<>();

    private final ExpireGuiConfig expireGuiConfig;
    private final ExpireCommandManager expireCommandManager;

    private final List<LocalDateTime> spamTest = new ArrayList<>();

    public ExpireGui(FAuction plugin, Player player, int page) {
        super(plugin, player, page);
        this.expireGuiConfig = plugin.getConfigurationManager().getExpireConfig();
        this.expireCommandManager = plugin.getExpireCommandManager();
        initGui(expireGuiConfig.getNameGui(), expireGuiConfig.getSize());
    }

    public void initializeItems() {
        TaskChain<ArrayList<Auction>> chain = expireCommandManager.getAuctions(player.getUniqueId());
        chain.sync(() -> {
            this.auctions = chain.getTaskData("auctions");
            if (this.auctions.size() == 0) {
                CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                issuerTarget.sendInfo(MessageKeys.NO_AUCTION);
                return;
            }
            for (Barrier barrier : expireGuiConfig.getBarrierBlocks()) {
                inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
            }
            for (Barrier barrier : expireGuiConfig.getAuctionGuiBlocks()) {
                inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
            }
            for (Barrier previous : expireGuiConfig.getPreviousBlocks()) {
                if (page > 1) {
                    inv.setItem(previous.getIndex(), createGuiItem(previous.getMaterial(), previous.getTitle(), previous.getDescription()));

                } else {
                    inv.setItem(previous.getRemplacement().getIndex(), createGuiItem(previous.getRemplacement().getMaterial(), previous.getRemplacement().getTitle(), previous.getRemplacement().getDescription()));
                }
            }

            for (Barrier next : expireGuiConfig.getNextBlocks()) {
                if ((this.expireGuiConfig.getExpireBlocks().size() * this.page) - this.expireGuiConfig.getExpireBlocks().size() < auctions.size() - this.expireGuiConfig.getExpireBlocks().size()) {
                    inv.setItem(next.getIndex(), createGuiItem(next.getMaterial(), next.getTitle(), next.getDescription()));
                } else {
                    inv.setItem(next.getRemplacement().getIndex(), createGuiItem(next.getRemplacement().getMaterial(), next.getRemplacement().getTitle(), next.getRemplacement().getDescription()));
                }
            }


            int id = (this.expireGuiConfig.getExpireBlocks().size() * this.page) - this.expireGuiConfig.getExpireBlocks().size();
            for (int index : expireGuiConfig.getExpireBlocks()) {
                String ownerName = this.auctions.get(id).getPlayerName();
                inv.setItem(index, createGuiItem(auctions.get(id), ownerName));
                id++;
                if (id >= (auctions.size())) break;
            }
            openInventory(player);
        }).execute();

    }


    private ItemStack createGuiItem(Auction auction, String playerName) {
        ItemStack item = auction.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        String title = expireGuiConfig.getTitle();
        if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
            title = title.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
        } else {
            title = title.replace("{ItemName}", item.getItemMeta().getDisplayName());
        }
        title = title.replace("{OwnerName}", playerName);
        title = title.replace("{Price}", String.valueOf(auction.getPrice()));
        title = FormatUtil.format(title);
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        List<String> listDescription = new ArrayList<>();

        for (String desc : expireGuiConfig.getDescription()) {
            if (item.getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                desc = desc.replace("{ItemName}", item.getType().name().replace('_', ' ').toLowerCase());
            } else {
                desc = desc.replace("{ItemName}", item.getItemMeta().getDisplayName());
            }
            Date expireDate = new Date((auction.getDate().getTime() + globalConfig.getTime() * 1000L));
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy 'a' HH:mm");
            desc = desc.replace("{ExpireTime}", formatter.format(expireDate));
            desc = desc.replace("{OwnerName}", playerName);
            desc = desc.replace("{Price}", String.valueOf(auction.getPrice()));
            if (desc.contains("lore")) {
                if (item.getLore() != null) {
                    listDescription.addAll(item.getLore());
                } else {
                    listDescription.add(desc.replace("{lore}", ""));
                }
            } else {
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
        if (inv.getHolder() != this || e.getInventory() != inv || player != e.getWhoClicked()) {
            return;
        }
        e.setCancelled(true);

        LocalDateTime clickTest = LocalDateTime.now();
        boolean isSpamming = spamTest.stream().anyMatch(d -> d.getHour() == clickTest.getHour() && d.getMinute() == clickTest.getMinute() && (d.getSecond() == clickTest.getSecond() || d.getSecond() == clickTest.getSecond() + 1 || d.getSecond() == clickTest.getSecond() - 1));
        if(isSpamming) {
            plugin.getLogger().warning("Warning : Spam gui expire Pseudo : " + player.getName());
            CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
            issuerTarget.sendInfo(MessageKeys.SPAM);
            return;
        } else {
            spamTest.add(clickTest);
        }

        Player p = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        for (int index : expireGuiConfig.getExpireBlocks()) {
            if (index == e.getRawSlot()) {

                if(auctions.isEmpty()) {
                    inv.close();
                    return;
                }

                int nb0 = expireGuiConfig.getExpireBlocks().get(0);
                int nb = ((e.getRawSlot() - nb0)) / 9;
                Auction auction = auctions.get((e.getRawSlot() - nb0) + ((this.expireGuiConfig.getExpireBlocks().size() * this.page) - this.expireGuiConfig.getExpireBlocks().size()) - nb * 2);

                if(plugin.getExpireAction().contains(auction.getId())) {
                    return;
                }
                plugin.getExpireAction().add((Integer)auction.getId());

                if (e.isLeftClick()) {
                    TaskChain<Auction> chainAuction = expireCommandManager.auctionExist(auction.getId());
                    chainAuction.sync(() -> {

                        if (chainAuction.getTaskData("auction") == null) {
                            plugin.getExpireAction().remove((Integer)auction.getId());
                            return;
                        }

                        if (!auction.getPlayerUuid().equals(player.getUniqueId())) {
                            plugin.getExpireAction().remove((Integer)auction.getId());
                            return;
                        }

                        if (player.getInventory().firstEmpty() == -1) {
                            player.getWorld().dropItem(player.getLocation(), auction.getItemStack());
                        } else {
                            player.getInventory().addItem(auction.getItemStack());
                        }

                        expireCommandManager.deleteAuction(auction.getId());
                        auctions.remove(auction);
                        CommandIssuer issuerTarget = plugin.getCommandManager().getCommandIssuer(player);
                        issuerTarget.sendInfo(MessageKeys.REMOVE_EXPIRE_SUCCESS);

                        plugin.getExpireAction().remove((Integer)auction.getId());

                        ExpireGui gui = new ExpireGui(plugin, player, 1);
                        gui.initializeItems();
                    }).execute();
                }
            }
        }


        for (Barrier previous : expireGuiConfig.getPreviousBlocks()) {
            if (e.getRawSlot() == previous.getIndex() && this.page > 1) {
                ExpireGui gui = new ExpireGui(plugin, p, this.page - 1);
                gui.initializeItems();
                gui.openInventory(p);
                break;
            }
        }
        for (Barrier next : expireGuiConfig.getNextBlocks()) {
            if (e.getRawSlot() == next.getIndex() && ((this.expireGuiConfig.getExpireBlocks().size() * this.page) - this.expireGuiConfig.getExpireBlocks().size() < auctions.size() - this.expireGuiConfig.getExpireBlocks().size()) && next.getMaterial() != next.getRemplacement().getMaterial()) {
                ExpireGui gui = new ExpireGui(plugin, p, this.page + 1);
                gui.initializeItems();
                gui.openInventory(p);
                break;
            }
        }
        for (Barrier auctionGui : expireGuiConfig.getAuctionGuiBlocks()) {
            if (e.getRawSlot() == auctionGui.getIndex()) {
                AuctionsGui gui = new AuctionsGui(plugin, p, 1);
                gui.initializeItems();
                break;
            }
        }
    }
}