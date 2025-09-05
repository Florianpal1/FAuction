package fr.florianpal.fauction.gui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.configurations.gui.AbstractGuiConfig;
import fr.florianpal.fauction.gui.subGui.*;
import fr.florianpal.fauction.managers.SpamManager;
import fr.florianpal.fauction.managers.commandmanagers.AuctionCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.CommandManager;
import fr.florianpal.fauction.managers.commandmanagers.ExpireCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.HistoricCommandManager;
import fr.florianpal.fauction.objects.Barrier;
import fr.florianpal.fauction.objects.BarrierMenu;
import fr.florianpal.fauction.objects.BarrierWithCategory;
import fr.florianpal.fauction.utils.FormatUtil;
import fr.florianpal.fauction.utils.ListUtil;
import fr.florianpal.fauction.utils.PlaceholderUtil;
import fr.florianpal.fauction.utils.PlayerHeadUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public abstract class AbstractGui implements InventoryHolder, Listener {

    protected Inventory inv;

    protected final FAuction plugin;

    protected Player player;

    protected int page;

    protected final GlobalConfig globalConfig;

    protected final CommandManager commandManager;

    protected final AuctionCommandManager auctionCommandManager;

    protected final ExpireCommandManager expireCommandManager;

    protected final HistoricCommandManager historicCommandManager;

    protected final SpamManager spamManager;

    protected AbstractGuiConfig abstractGuiConfig;

    protected DecimalFormat df;

    protected SimpleDateFormat dateFormater;

    protected AbstractGui(FAuction plugin, Player player, int page, AbstractGuiConfig abstractGuiConfig) {

        this.plugin = plugin;
        this.player = player;
        this.page = page;
        this.commandManager = plugin.getCommandManager();
        this.inv = null;
        this.globalConfig = plugin.getConfigurationManager().getGlobalConfig();
        this.auctionCommandManager = plugin.getAuctionCommandManager();
        this.expireCommandManager = plugin.getExpireCommandManager();
        this.historicCommandManager = plugin.getHistoricCommandManager();
        this.spamManager = plugin.getSpamManager();
        this.abstractGuiConfig = abstractGuiConfig;

        if (abstractGuiConfig == null) {
            plugin.getLogger().severe("Error when load custom menu. Cannot found config");
            return;
        }

        df = new DecimalFormat(plugin.getConfigurationManager().getGlobalConfig().getDecimalFormat());
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));

        dateFormater = new SimpleDateFormat(globalConfig.getDateFormat());

        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugins()[0]);
    }

    public void initialize() {

        initGui(abstractGuiConfig.getNameGui(), abstractGuiConfig.getSize());
        initBarrier();
        openInventory(player);
    }

    protected void initGui(String title, int size) {
        if (abstractGuiConfig.getType() == InventoryType.CHEST) {
            inv = Bukkit.createInventory(this, size, FormatUtil.format(title));
        } else {
            inv = Bukkit.createInventory(this, abstractGuiConfig.getType(), FormatUtil.format(title));
        }
    }

    public void openInventory(Player p) {
        p.openInventory(this.inv);
    }

    protected void initBarrier() {

        for (Barrier barrier : abstractGuiConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(getItemStack(barrier, false)));
        }

        for (Barrier barrier : abstractGuiConfig.getExpireBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(getItemStack(barrier, false)));
        }

        for (Barrier p : abstractGuiConfig.getPlayerBlocks()) {
            inv.setItem(p.getIndex(), createGuiItem(getItemStack(p, false)));
        }

        for (Barrier auctionGui : abstractGuiConfig.getAuctionGuiBlocks()) {
            inv.setItem(auctionGui.getIndex(), createGuiItem(getItemStack(auctionGui, false)));
        }

        for (Barrier close : abstractGuiConfig.getCloseBlocks()) {
            inv.setItem(close.getIndex(), createGuiItem(getItemStack(close, false)));
        }

        for (Barrier historic : abstractGuiConfig.getHistoricBlocks()) {
            inv.setItem(historic.getIndex(), createGuiItem(getItemStack(historic, false)));
        }

        for (Barrier menu : abstractGuiConfig.getMenuBlocks()) {
            inv.setItem(menu.getIndex(), createGuiItem(getItemStack(menu, false)));
        }

        for (Barrier comamnd : abstractGuiConfig.getCommandBlocks()) {
            inv.setItem(comamnd.getIndex(), createGuiItem(getItemStack(comamnd, false)));
        }
    }

    public ItemStack createGuiItem(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || meta.getDisplayName() == null || meta.getLore() == null) {
            return itemStack;
        }
        String name = FormatUtil.format(meta.getDisplayName());
        List<String> descriptions = new ArrayList<>();
        for (String desc : meta.getLore()) {

            desc = FormatUtil.format(desc);
            descriptions.add(desc);
        }
        meta.setDisplayName(name);
        meta.setLore(descriptions);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

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

            List<String> descriptions = new ArrayList<>();
            for (String desc : barrier.getDescription()) {
                desc = FormatUtil.format(desc);
                desc = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), player, desc);
                descriptions.add(desc);
            }

            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                String title = barrier.getTitle();
                title = PlaceholderUtil.parsePlaceholder(plugin.isPlaceholderAPIEnabled(), player, title);
                meta.setDisplayName(FormatUtil.format(title));
                meta.setLore(descriptions);
                meta.setCustomModelData(barrier.getCustomModelData());
                itemStack.setItemMeta(meta);
            }
        }
        return itemStack;
    }

    public boolean guiClick(InventoryClickEvent e) {

        Optional<BarrierWithCategory> auctionGuiBarrierOptional = abstractGuiConfig.getAuctionGuiBlocks().stream().filter(auctionGui -> e.getRawSlot() == auctionGui.getIndex()).findFirst();
        if (auctionGuiBarrierOptional.isPresent()) {

            FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                AuctionsGui gui = new AuctionsGui(plugin, player, auctions, 1, auctionGuiBarrierOptional.get().getCategory(), null);
                gui.initialize();
            }).execute();
            return true;
        }

        Optional<BarrierWithCategory> expireGuiBarrierOptional = abstractGuiConfig.getExpireBlocks().stream().filter(expire -> e.getRawSlot() == expire.getIndex()).findFirst();
        if (expireGuiBarrierOptional.isPresent()) {

            FAuction.newChain().asyncFirst(() -> expireCommandManager.getExpires(player.getUniqueId())).syncLast(auctions -> {
                ExpireGui gui = new ExpireGui(plugin, player, auctions, 1, expireGuiBarrierOptional.get().getCategory(), null);
                gui.initialize();
            }).execute();
            return true;
        }

        boolean isClose = abstractGuiConfig.getCloseBlocks().stream().anyMatch(close -> e.getRawSlot() == close.getIndex());
        if (isClose) {

            player.closeInventory();
            return true;
        }

        Optional<Barrier> commandBarrierOptional = abstractGuiConfig.getCommandBlocks().stream().filter(close -> e.getRawSlot() == close.getIndex()).findFirst();
        if (commandBarrierOptional.isPresent()) {

            Bukkit.dispatchCommand(plugin.getServer().getConsoleSender(), commandBarrierOptional.get().getValue());
            return true;
        }

        Optional<BarrierWithCategory> playerGuiBarrierOptional = abstractGuiConfig.getPlayerBlocks().stream().filter(p -> e.getRawSlot() == p.getIndex()).findFirst();
        if (playerGuiBarrierOptional.isPresent()) {

            FAuction.newChain().asyncFirst(() ->  auctionCommandManager.getAuctions(player.getUniqueId())).syncLast(auctions -> {
                PlayerViewGui gui = new PlayerViewGui(plugin, player, auctions, 1, playerGuiBarrierOptional.get().getCategory(), null);
                gui.initialize();
            }).execute();
            return true;
        }

        Optional<BarrierWithCategory> historicGuiBarrierOptional = abstractGuiConfig.getHistoricBlocks().stream().filter(p -> e.getRawSlot() == p.getIndex()).findFirst();
        if (historicGuiBarrierOptional.isPresent()) {

            FAuction.newChain().asyncFirst(() -> historicCommandManager.getHistorics(player.getUniqueId())).syncLast(historics -> {
                HistoricGui gui = new HistoricGui(plugin, player, ListUtil.historicToAuction(historics), 1, historicGuiBarrierOptional.get().getCategory(), null);
                gui.initialize();
            }).execute();
            return true;
        }

        Optional<BarrierMenu> menuGuiBarrierOptional = abstractGuiConfig.getMenuBlocks().stream().filter(p -> e.getRawSlot() == p.getIndex()).findFirst();
        if (menuGuiBarrierOptional.isPresent()) {
            MainGui gui = new MainGui(plugin, menuGuiBarrierOptional.get().getId(), player, 1);
            gui.initialize();
            return true;
        }

        return false;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
