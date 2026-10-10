package fr.florianpal.fauction.api.importer.event;

import fr.florianpal.fauction.api.importer.ImportReport;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * An import finished, whatever its outcome ({@link ImportReport#status()}). Fired from the import
 * thread, asynchronously on a server : a listener must not touch the Bukkit API that needs the main
 * thread.
 */
public class ImportFinishEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final ImportReport report;

    public ImportFinishEvent(ImportReport report) {
        super(!Bukkit.isPrimaryThread());
        this.report = report;
    }

    public ImportReport getReport() {
        return report;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
