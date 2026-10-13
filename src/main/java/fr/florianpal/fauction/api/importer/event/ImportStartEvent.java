package fr.florianpal.fauction.api.importer.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import fr.florianpal.fauction.api.importer.ImportDataType;

/**
 * An import is about to start. Cancelling it prevents anything from being read.
 * <p>
 * No {@code AuctionAddEvent} nor {@code ExpireAddEvent} is fired for the imported rows : listen to
 * {@link ImportFinishEvent} instead.
 */
public class ImportStartEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final String importerId;

    private final Set<ImportDataType> types;

    private final boolean dryRun;

    private boolean cancelled;

    public ImportStartEvent(String importerId, Set<ImportDataType> types, boolean dryRun) {
        super(!Bukkit.isPrimaryThread());
        this.importerId = importerId;
        this.types = Set.copyOf(types);
        this.dryRun = dryRun;
    }

    public String getImporterId() {
        return importerId;
    }

    public Set<ImportDataType> getTypes() {
        return types;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
