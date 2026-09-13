package fr.florianpal.fauction.utils.scheduling;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replaces {@code co.aikar.taskchain} : same asyncFirst/async/syncLast/sync/execute vocabulary as
 * before, but built on FoliaLib so the sync steps go through the region scheduler of {@code entity}
 * under Folia (or the global region scheduler when no entity is given), instead of the
 * BukkitScheduler TaskChain relied on and that Folia does not support.
 */
public final class SchedulerChain<T> {

    private final FoliaLib foliaLib;

    private final Entity entity;

    private final Logger logger;

    private final CompletableFuture<T> future;

    private SchedulerChain(FoliaLib foliaLib, Entity entity, Logger logger, CompletableFuture<T> future) {
        this.foliaLib = foliaLib;
        this.entity = entity;
        this.logger = logger;
        this.future = future;
    }

    /**
     * A chain whose sync steps run on the global region (no player/entity is involved).
     */
    public static SchedulerChain<Void> newChain(FoliaLib foliaLib, Logger logger) {
        return new SchedulerChain<>(foliaLib, null, logger, CompletableFuture.completedFuture(null));
    }

    /**
     * A chain whose sync steps run on the region owning {@code entity} (e.g. a player whose
     * inventory the chain is about to touch), even if that region moves under Folia.
     */
    public static SchedulerChain<Void> newChain(FoliaLib foliaLib, Logger logger, Entity entity) {
        return new SchedulerChain<>(foliaLib, entity, logger, CompletableFuture.completedFuture(null));
    }

    public <U> SchedulerChain<U> asyncFirst(Supplier<U> supplier) {
        return chainAsync(ignored -> supplier.get());
    }

    public SchedulerChain<Void> async(Runnable runnable) {
        return chainAsync(ignored -> {
            runnable.run();
            return null;
        });
    }

    public SchedulerChain<Void> asyncLast(Consumer<T> consumer) {
        return chainAsync(value -> {
            consumer.accept(value);
            return null;
        });
    }

    public SchedulerChain<Void> sync(Runnable runnable) {
        return chainSync(ignored -> {
            runnable.run();
            return null;
        });
    }

    public SchedulerChain<Void> syncLast(Consumer<T> consumer) {
        return chainSync(value -> {
            consumer.accept(value);
            return null;
        });
    }

    public void execute() {
        future.exceptionally(error -> {
            logError(error);
            return null;
        });
    }

    /**
     * @param doneCallback runs once the chain finishes, whether it succeeded or not, on the thread
     *                      that completed the last step.
     */
    public void execute(Runnable doneCallback) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                logError(error);
            }
            doneCallback.run();
        });
    }

    private <U> SchedulerChain<U> chainAsync(Function<T, U> step) {
        CompletableFuture<U> next = future.thenCompose(value -> runAsync(() -> step.apply(value)));
        return new SchedulerChain<>(foliaLib, entity, logger, next);
    }

    private <U> SchedulerChain<U> chainSync(Function<T, U> step) {
        CompletableFuture<U> next = future.thenCompose(value -> runSync(() -> step.apply(value)));
        return new SchedulerChain<>(foliaLib, entity, logger, next);
    }

    private <U> CompletableFuture<U> runAsync(Supplier<U> step) {
        CompletableFuture<U> result = new CompletableFuture<>();
        foliaLib.getScheduler().runAsync(task -> complete(result, step));
        return result;
    }

    private <U> CompletableFuture<U> runSync(Supplier<U> step) {
        CompletableFuture<U> result = new CompletableFuture<>();
        if (entity != null) {
            // The player may have logged off (or the entity died/unloaded) while the async step ran ;
            // the fallback quietly skips the sync step instead of failing.
            foliaLib.getScheduler().runAtEntityWithFallback(
                    entity,
                    task -> complete(result, step),
                    () -> result.complete(null)
            );
        } else {
            foliaLib.getScheduler().runNextTick(task -> complete(result, step));
        }
        return result;
    }

    private <U> void complete(CompletableFuture<U> result, Supplier<U> step) {
        try {
            result.complete(step.get());
        } catch (Throwable t) {
            result.completeExceptionally(t);
        }
    }

    private void logError(Throwable error) {
        logger.log(Level.SEVERE, "Unhandled exception in a scheduler chain", error);
    }
}
