package fr.florianpal.fauction.events;

import fr.florianpal.fauction.objects.Bid;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class BidWonEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final OfflinePlayer winner;

    private final Bid bid;

    public BidWonEvent(OfflinePlayer winner, Bid bid) {

        this.winner = winner;
        this.bid = bid;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public OfflinePlayer getWinner() {
        return winner;
    }

    public Bid getBid() {
        return bid;
    }
}
