package fr.florianpal.fauction.events;

import fr.florianpal.fauction.enums.CancelReason;
import fr.florianpal.fauction.objects.Bid;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class BidCancelEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;

    private final Bid bid;

    private final CancelReason cancelReason;

    public BidCancelEvent(Player player, Bid bid, CancelReason cancelReason) {

        this.player = player;
        this.bid = bid;
        this.cancelReason = cancelReason;
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

    public CancelReason getCancelReason() {
        return cancelReason;
    }
}
