package fr.florianpal.fauction.gui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.gui.AbstractGuiWithAuctionsConfig;
import fr.florianpal.fauction.objects.*;
import fr.florianpal.fauction.utils.FormatUtil;
import fr.florianpal.fauction.utils.ListUtil;
import fr.florianpal.fauction.utils.PlaceholderUtil;
import fr.florianpal.fauction.utils.PlayerHeadUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.*;

/**
 * Mirrors AbstractGuiWithAuctions for Bid, which is intentionally not an Auction subclass (a bid has
 * no immediate sale price, a current bidder that changes over time, and its own end date), so both
 * hierarchies stay independent instead of forcing a fragile shared supertype.
 */
public abstract class AbstractGuiWithBids extends AbstractGui {

    protected List<Bid> bids;

    protected Category category;

    protected Sort sort;

    protected AbstractGuiWithAuctionsConfig abstractGuiWithBidsConfig;

    protected AbstractGuiWithBids(FAuction plugin, Player player, int page, List<Bid> bids, Category category, Sort sort, AbstractGuiWithAuctionsConfig abstractGuiWithBidsConfig) {
        super(plugin, player, page, abstractGuiWithBidsConfig);
        this.bids = bids;
        this.abstractGuiWithBidsConfig = abstractGuiWithBidsConfig;

        if (category == null) category = plugin.getConfigurationManager().getCategoriesConfig().getDefault();
        if (sort == null) sort = plugin.getConfigurationManager().getSortConfig().getDefault();
        this.category = category;
        this.sort = sort;

        this.bids = ListUtil.getBidsByCategory(bids, category);
        this.bids = ListUtil.applyBidSorting(this.bids, sort);
    }

    @Override
    protected void initGui(String title, int size) {

        title = title.replace("{Page}", String.valueOf(this.page));
        if (this.bids != null && !abstractGuiWithBidsConfig.getBaseBlocks().isEmpty()) {
            title = title.replace("{TotalPage}", String.valueOf(((this.bids.size() - 1) / abstractGuiWithBidsConfig.getBaseBlocks().size()) + 1));
        } else {
            title = title.replace("{TotalPage}", "1");
        }
        this.inv = Bukkit.createInventory(this, abstractGuiWithBidsConfig.getSize(), FormatUtil.format(title));
    }

    protected void initBarrier() {

        for (Barrier previous : abstractGuiWithBidsConfig.getPreviousBlocks()) {
            if (page > 1) {
                inv.setItem(previous.getIndex(), createGuiItem(getItemStack(previous, false)));
            } else {
                inv.setItem(previous.getRemplacement().getIndex(), createGuiItem(getItemStack(previous, true)));
            }
        }

        for (Barrier next : abstractGuiWithBidsConfig.getNextBlocks()) {
            if ((this.abstractGuiWithBidsConfig.getBaseBlocks().size() * this.page) - this.abstractGuiWithBidsConfig.getBaseBlocks().size() < bids.size() - this.abstractGuiWithBidsConfig.getBaseBlocks().size()) {
                inv.setItem(next.getIndex(), createGuiItem(getItemStack(next, false)));
            } else {
                inv.setItem(next.getRemplacement().getIndex(), createGuiItem(getItemStack(next, true)));
            }
        }

        for (Barrier categoryBlock : abstractGuiWithBidsConfig.getCategoriesBlocks()) {
            inv.setItem(categoryBlock.getIndex(), createGuiItem(getItemStack(categoryBlock, false)));
        }

        for (Barrier sortBlock : abstractGuiWithBidsConfig.getSortingBlocks()) {
            inv.setItem(sortBlock.getIndex(), createGuiItem(getItemStack(sortBlock, false)));
        }

        super.initBarrier();
    }

    @Override
    public void initialize() {

        initGui(abstractGuiConfig.getNameGui(), abstractGuiConfig.getSize());
        initBarrier();

        if (!bids.isEmpty()) {
            int id = (this.abstractGuiWithBidsConfig.getBaseBlocks().size() * this.page) - this.abstractGuiWithBidsConfig.getBaseBlocks().size();
            for (var index : abstractGuiWithBidsConfig.getBaseBlocks().entrySet()) {
                inv.setItem(index.getValue(), createGuiItem(bids.get(id)));
                id++;
                if (id >= (bids.size())) break;
            }
        }
        openInventory(player);
    }

    @Override
    public ItemStack createGuiItem(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || meta.getDisplayName() == null || meta.getLore() == null) {
            return itemStack;
        }
        String name = FormatUtil.format(meta.getDisplayName());
        List<String> descriptions = new ArrayList<>();
        for (String desc : meta.getLore()) {

            if (this.bids != null) {
                desc = desc.replace("{TotalSale}", String.valueOf(this.bids.size()));
            } else {
                desc = desc.replace("{TotalSale}", "0");
            }

            desc = FormatUtil.format(desc);
            descriptions.add(desc);
        }

        if (abstractGuiWithBidsConfig.isHideFlag()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        meta.setDisplayName(name);
        meta.setLore(descriptions);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public ItemStack createGuiItem(Bid bid) {

        ItemStack item = bid.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();

        String title = abstractGuiWithBidsConfig.getTitle();
        title = FormatUtil.titleItemFormat(item, "{Item}", title);

        title = title.replace("{OwnerName}", bid.getSellerName());
        title = title.replace("{StartPrice}", df.format(bid.getStartPrice()));
        title = title.replace("{CurrentBid}", df.format(bid.getCurrentPrice()));
        title = title.replace("{CurrentBidder}", bid.hasBidder() ? bid.getCurrentBidderName() : "-");

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(bid.getSellerUuid());
        if (offlinePlayer != null) {
            title = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, title);
        }

        title = FormatUtil.format(title);
        List<String> listDescription = new ArrayList<>();

        for (String desc : abstractGuiWithBidsConfig.getDescription()) {
            desc = FormatUtil.titleItemFormat(item, "{Item}", desc);

            desc = desc.replace("{TotalSale}", String.valueOf(this.bids.size()));
            desc = desc.replace("{OwnerName}", bid.getSellerName());

            if (offlinePlayer != null) {
                desc = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), offlinePlayer, desc);
            }

            desc = desc.replace("{StartPrice}", df.format(bid.getStartPrice()));
            desc = desc.replace("{CurrentBid}", df.format(bid.getCurrentPrice()));
            desc = desc.replace("{CurrentBidder}", bid.hasBidder() ? bid.getCurrentBidderName() : "-");
            desc = desc.replace("{ExpireTime}", dateFormater.format(bid.getEndDate()));

            Duration duration = Duration.between(new Date().toInstant(), bid.getEndDate().toInstant());
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

            if (abstractGuiWithBidsConfig.isHideFlag()) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }

            if (abstractGuiWithBidsConfig.isReplaceTitle()) {
                meta.setDisplayName(title);
            }
            meta.setLore(listDescription);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public ItemStack getItemStack(Barrier barrier, boolean isRemplacement) {
        ItemStack itemStack;
        if (isRemplacement) {
            itemStack = getItemStack(barrier.getRemplacement(), false);
        } else {

            itemStack = new ItemStack(barrier.getMaterial(), 1);
            if (barrier.getMaterial() == Material.PLAYER_HEAD) {

                PlayerHeadUtil.addTexture(itemStack, barrier.getTexture());
                itemStack.setAmount(1);
            }

            var previousCategories = new LinkedList<Category>();
            var nextCategories = new LinkedList<Category>();
            boolean isFound = false;
            for (var entry : categoriesConfig.getCategories().entrySet()) {

                if (entry.getValue().getId().equals(category.getId())) {
                    isFound = true;
                } else if (isFound) {
                    nextCategories.add(entry.getValue());
                } else {
                    previousCategories.add(entry.getValue());
                }
            }

            List<String> descriptions = new ArrayList<>();
            for (String desc : barrier.getDescription()) {

                if (desc.equals("{previousCategories}")) {

                    for (var previous : previousCategories) {
                        descriptions.add(previous.getDisplayName());
                    }
                } else if (desc.equals("{nextCategories}")) {

                    for (var next : nextCategories) {
                        descriptions.add(next.getDisplayName());
                    }
                } else {
                    desc = FormatUtil.format(desc);
                    desc = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), player, desc);
                    desc = desc.replace("{currentCategory}", category != null ? category.getDisplayName() : "");
                    desc = desc.replace("{sortDisplayName}", category != null ? sort.getDisplayName() : "");
                    descriptions.add(desc);
                }

            }


            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {

                if (abstractGuiWithBidsConfig.isHideFlag()) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                }

                String name = barrier.getTitle().replace("{categoryDisplayName}", category != null ? category.getDisplayName() : "");
                name = name.replace("{sortDisplayName}", category != null ? category.getDisplayName() : "");
                name = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), player, name);
                meta.setDisplayName(FormatUtil.format(name));
                meta.setLore(descriptions);
                meta.setCustomModelData(barrier.getCustomModelData());
                itemStack.setItemMeta(meta);
            }
        }
        return itemStack;
    }

    public boolean guiClick(InventoryClickEvent e) {

        boolean isBarrier = abstractGuiWithBidsConfig.getBarrierBlocks().stream().anyMatch(b -> b.getIndex() == e.getRawSlot());
        if (isBarrier) {
            return true;
        }

        boolean isPrevious = abstractGuiWithBidsConfig.getPreviousBlocks().stream().anyMatch(b -> b.getIndex() == e.getRawSlot() && this.page > 1);
        if (isPrevious) {

            previousAction();
            return true;
        }

        boolean isNext = abstractGuiWithBidsConfig.getNextBlocks().stream().anyMatch(next -> e.getRawSlot() == next.getIndex() && ((this.abstractGuiWithBidsConfig.getBaseBlocks().size() * this.page) - this.abstractGuiWithBidsConfig.getBaseBlocks().size() < bids.size() - this.abstractGuiWithBidsConfig.getBaseBlocks().size()));
        if (isNext) {

            nextAction();
            return true;
        }

        boolean isCategory = abstractGuiWithBidsConfig.getCategoriesBlocks().stream().anyMatch(c -> e.getRawSlot() == c.getIndex());
        if (isCategory) {

            Category nextCategory = plugin.getConfigurationManager().getCategoriesConfig().getNext(category);
            categoryAction(nextCategory);
            return true;
        }

        boolean isSorting = abstractGuiWithBidsConfig.getSortingBlocks().stream().anyMatch(c -> e.getRawSlot() == c.getIndex());
        if (isSorting) {

            Sort nextSort = plugin.getConfigurationManager().getSortConfig().getNext(sort);
            sortingAction(nextSort);
            return true;
        }

        return super.guiClick(e);
    }

    protected abstract void previousAction();

    protected abstract void nextAction();

    protected abstract void categoryAction(Category nextCategory);

    protected abstract void sortingAction(Sort nextSort);

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
