package fr.florianpal.fauction.events;

import fr.florianpal.fauction.objects.Bid;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CacheBidReloadEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Map<UUID, List<Bid>> cache;

    public CacheBidReloadEvent(Map<UUID, List<Bid>> cache) {

        this.cache = cache;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public Map<UUID, List<Bid>> getCache() {
        return cache;
    }
}
