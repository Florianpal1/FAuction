package fr.florianpal.fauction.gui.subGui;

import co.aikar.commands.CommandIssuer;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.AmountGuiConfig;
import fr.florianpal.fauction.enums.InterfaceType;
import fr.florianpal.fauction.gui.AbstractGui;
import fr.florianpal.fauction.gui.GuiInterface;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.objects.AmountBarrier;
import fr.florianpal.fauction.objects.Barrier;
import fr.florianpal.fauction.objects.Bill;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AmountGui extends AbstractGui implements GuiInterface {

    private double amount;

    private final Bill bill;

    private final AmountGuiConfig amountGuiConfig;

    protected AmountGui(FAuction plugin, Player player, int page, Bill bill) {
        super(plugin, player, page);
        this.bill = bill;
        this.amountGuiConfig = plugin.getConfigurationManager().getAmountGuiConfig();
        if(bill.getPlayerBidderUuid() != null) {
            this.amount = bill.getBet();
        } else {
            this.amount = bill.getPrice();
        }
        initGui(amountGuiConfig.getNameGui(), amountGuiConfig.getSize());
    }

    @Override
    public void initializeItems() {
        for (Barrier barrier : amountGuiConfig.getBarrierBlocks()) {
            inv.setItem(barrier.getIndex(), createGuiItem(barrier.getMaterial(), barrier.getTitle(), barrier.getDescription()));
        }

        for (Barrier close : amountGuiConfig.getCloseBlocks()) {
            inv.setItem(close.getIndex(), createGuiItem(close.getMaterial(), close.getTitle(), close.getDescription()));
        }

        for (Barrier confirm : amountGuiConfig.getConfirmBlocks()) {
            inv.setItem(confirm.getIndex(), createGuiItem(confirm.getMaterial(), confirm.getTitle(), confirm.getDescription()));
        }

        for (AmountBarrier amountBarrier : amountGuiConfig.getAmountBlocks()) {
            inv.setItem(amountBarrier.getIndex(), createGuiItem(amountBarrier));
        }
        openInventory(player);
    }

    @Override
    public ItemStack createGuiItem(Material material, String name, List<String> description) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        name = ChatColor.translateAlternateColorCodes('&', name);
        name = name.replace("{Amount}", String.valueOf(amount));
        List<String> descriptions = new ArrayList<>();
        for (String desc : description) {
            desc = ChatColor.translateAlternateColorCodes('&', desc);
            desc = desc.replace("{Amount}", String.valueOf(amount));
            descriptions.add(desc);
        }
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(descriptions);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createGuiItem(AmountBarrier amountBarrier) {
        ItemStack item = new ItemStack(amountBarrier.getMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        String name = ChatColor.translateAlternateColorCodes('&', amountBarrier.getTitle());
        name = name.replace("{Offer}", String.valueOf(amountBarrier.getAmount()));
        List<String> descriptions = new ArrayList<>();
        for (String desc : amountBarrier.getDescription()) {
            desc = ChatColor.translateAlternateColorCodes('&', desc);
            desc = desc.replace("{Offer}", String.valueOf(amountBarrier.getAmount()));
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

        for (Barrier close : amountGuiConfig.getCloseBlocks()) {
            if (e.getRawSlot() == close.getIndex()) {
                inv.close();
                return;
            }
        }

        for (Barrier confirm : amountGuiConfig.getConfirmBlocks()) {
            if (e.getRawSlot() == confirm.getIndex()) {
                if(amount > bill.getBet()) {
                    ConfirmGui confirmGui = new ConfirmGui(plugin, player, page, bill, amount, InterfaceType.BILL);
                    confirmGui.initializeItems();
                } else {
                    CommandIssuer issuerTarget = commandManager.getCommandIssuer(player);
                    double bet = bill.getPrice();
                    if(bill.getPlayerBidderUuid() != null) {
                        bet = bill.getBet();
                    }
                    issuerTarget.sendInfo(MessageKeys.AMOUNT_LESS_THAN_BET, "{Offer}", String.valueOf(amount), "{Bet}", String.valueOf(bet));
                }
                return;
            }
        }

        for (AmountBarrier amountBarrier : amountGuiConfig.getAmountBlocks()) {
            if (e.getRawSlot() == amountBarrier.getIndex()) {
                amount = amount + amountBarrier.getAmount();
                initializeItems();
                return;
            }
        }
    }
}
