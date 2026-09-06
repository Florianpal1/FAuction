package fr.florianpal.fauction.events;

import fr.florianpal.fauction.objects.Bid;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class BidPlaceEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;

    private final Bid bid;

    private final double amount;

    public BidPlaceEvent(Player player, Bid bid, double amount) {

        this.player = player;
        this.bid = bid;
        this.amount = amount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public Player getPlayer() {
        return player;
    }

    public Bid getBid() {
        return bid;
    }

    public double getAmount() {
        return amount;
    }
}
