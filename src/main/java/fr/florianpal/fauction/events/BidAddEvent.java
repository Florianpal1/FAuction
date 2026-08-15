package fr.florianpal.fauction.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class BidAddEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;

    private final ItemStack itemStack;

    private final double startPrice;

    public BidAddEvent(Player player, ItemStack itemStack, double startPrice) {

        this.player = player;
        this.itemStack = itemStack;
        this.startPrice = startPrice;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public double getStartPrice() {
        return startPrice;
    }
}
