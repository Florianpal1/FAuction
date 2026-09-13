package fr.florianpal.fauction.utils.scheduling;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every scheduler call is answered inline on the calling thread, so a chain completes before
 * execute() returns and the assertions can stay straightforward. What matters here is which steps
 * run, in which order, and on which of the two schedulers.
 */
class SchedulerChainTest {

    private FoliaLib foliaLib;

    private Entity entity;

    private Logger logger;

    /**
     * Which scheduler each step went through, in order.
     */
    private List<String> schedulers;

    /**
     * Drives the fallback of runAtEntityWithFallback : true reproduces a player who left while the
     * async step was running, which only ever happens under Folia.
     */
    private AtomicBoolean entityGone;

    @BeforeEach
    void setUp() {
        foliaLib = mock(FoliaLib.class);
        entity = mock(Entity.class);
        logger = mock(Logger.class);
        schedulers = new ArrayList<>();
        entityGone = new AtomicBoolean(false);

        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(foliaLib.getScheduler()).thenReturn(scheduler);

        doAnswer(invocation -> {
            schedulers.add("async");
            invocation.<Consumer<WrappedTask>>getArgument(0).accept(null);
            return CompletableFuture.completedFuture(null);
        }).when(scheduler).runAsync(any());

        doAnswer(invocation -> {
            schedulers.add("global");
            invocation.<Consumer<WrappedTask>>getArgument(0).accept(null);
            return CompletableFuture.completedFuture(null);
        }).when(scheduler).runNextTick(any());

        doAnswer(invocation -> {
            if (entityGone.get()) {
                invocation.<Runnable>getArgument(2).run();
            } else {
                schedulers.add("entity");
                invocation.<Consumer<WrappedTask>>getArgument(1).accept(null);
            }
            return new CompletableFuture<>();
        }).when(scheduler).runAtEntityWithFallback(any(), any(), any());
    }

    @Test
    @DisplayName("The sync step runs on the region of the entity, with the value of the async step")
    void runsTheStepsInOrderAndPassesTheValue() {

        AtomicReference<String> seen = new AtomicReference<>();

        SchedulerChain.newChain(foliaLib, logger, entity)
                .asyncFirst(() -> "auction")
                .syncLast(seen::set)
                .execute();

        assertEquals("auction", seen.get());
        assertEquals(List.of("async", "entity"), schedulers);
    }

    @Test
    @DisplayName("A chain without an entity runs its sync step on the global region, and never skips it")
    void globalChainNeverSkipsItsSyncStep() {

        AtomicBoolean ran = new AtomicBoolean(false);
        entityGone.set(true);

        SchedulerChain.newChain(foliaLib, logger)
                .asyncFirst(() -> "auction")
                .syncLast(value -> ran.set(true))
                .execute();

        assertTrue(ran.get());
        assertEquals(List.of("async", "global"), schedulers);
    }

    @Test
    @DisplayName("An entity that is gone skips the sync step, and the chain still finishes")
    void skipsTheSyncStepWhenTheEntityIsGone() {

        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicBoolean finished = new AtomicBoolean(false);
        entityGone.set(true);

        SchedulerChain.newChain(foliaLib, logger, entity)
                .asyncFirst(() -> "auction")
                .syncLast(value -> ran.set(true))
                .execute(() -> finished.set(true));

        assertFalse(ran.get());
        assertTrue(finished.get(), "the claim of the chain has to be released even when the step is skipped");
    }

    @Test
    @DisplayName("The repair gets what the async step committed, and runs off the region")
    void runsTheRepairWhenTheEntityIsGone() {

        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicReference<String> repaired = new AtomicReference<>();
        entityGone.set(true);

        SchedulerChain.newChain(foliaLib, logger, entity)
                .asyncFirst(() -> "auction")
                .syncLast(value -> ran.set(true), repaired::set)
                .execute();

        assertFalse(ran.get());
        assertEquals("auction", repaired.get(), "the repair has to see the row the async step claimed");
        assertEquals(List.of("async", "async"), schedulers, "the repair hits the database, it must not run on a region thread");
    }

    @Test
    @DisplayName("A repair that throws does not stop the chain from finishing")
    void aFailingRepairStillFinishesTheChain() {

        AtomicBoolean finished = new AtomicBoolean(false);
        entityGone.set(true);

        SchedulerChain.newChain(foliaLib, logger, entity)
                .asyncFirst(() -> "auction")
                .syncLast(value -> {
                }, value -> {
                    throw new IllegalStateException("database down");
                })
                .execute(() -> finished.set(true));

        assertTrue(finished.get());
    }

    @Test
    @DisplayName("A skipped syncFirst produces null, which is what keeps a pending payment pending")
    void syncFirstProducesNullWhenTheEntityIsGone() {

        // The shape CurrencyScheduler relies on : the row is only cleared when the payment really
        // went through, so a player who left is still owed their money on the next pass.
        AtomicBoolean deleted = new AtomicBoolean(false);
        entityGone.set(true);

        SchedulerChain.newChain(foliaLib, logger, entity)
                .syncFirst(() -> Boolean.TRUE)
                .asyncLast(given -> {
                    assertNull(given);
                    if (Boolean.TRUE.equals(given)) {
                        deleted.set(true);
                    }
                })
                .execute();

        assertFalse(deleted.get());
    }

    @Test
    @DisplayName("A syncFirst that runs hands its value to the rest of the chain")
    void syncFirstPassesItsValueOn() {

        AtomicBoolean deleted = new AtomicBoolean(false);

        SchedulerChain.newChain(foliaLib, logger, entity)
                .syncFirst(() -> Boolean.TRUE)
                .asyncLast(given -> deleted.set(Boolean.TRUE.equals(given)))
                .execute();

        assertTrue(deleted.get());
        assertEquals(List.of("entity", "async"), schedulers);
    }
}
