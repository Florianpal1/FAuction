package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.api.importer.DataImporter;
import fr.florianpal.fauction.api.importer.ImportAvailability;
import fr.florianpal.fauction.api.importer.ImportContext;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportReport;
import fr.florianpal.fauction.api.importer.event.ImportFinishEvent;
import fr.florianpal.fauction.api.importer.event.ImportStartEvent;
import fr.florianpal.fauction.enums.SQLType;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the imports : one at a time, off the main thread, refused in SQLite mode.
 * <p>
 * The module pushes its rows into an {@link ImportRun}, which validates them, leaves out the ones a
 * previous import brought, and writes the others in batches, each batch in a single transaction with
 * its journal entries. At the end the caches are refreshed, a detailed report is written and
 * {@link ImportFinishEvent} is fired.
 */
public class ImportManager {

    public enum StartResult {
        STARTED,

        /**
         * SQLite mode : the auctions are rewritten from the memory cache on shutdown, an import
         * written to the database would be lost.
         */
        REFUSED_SQLITE,

        UNKNOWN_IMPORTER,

        ALREADY_RUNNING,

        /**
         * None of the types asked with {@code --types} is supported by the module.
         */
        NO_TYPE,

        CANCELLED_BY_EVENT
    }

    /**
     * Everything the manager needs from the server, so the tests can provide their own.
     *
     * @param nameLookup     the name the server knows for a UUID, or {@code null}.
     * @param asyncExecutor  runs the import off the main thread.
     * @param syncExecutor   runs a task on the main thread, for {@code callSync}.
     * @param refreshCaches  reloads the caches of the auctions, expired items and history.
     * @param eventCaller    fires a Bukkit event.
     * @param pluginLookup   a loaded plugin by name, or {@code null}.
     * @param reportsFolder  where the detailed reports are written.
     */
    public record Environment(Logger logger, SQLType sqlType, ImporterRegistryImpl registry, ImportStore store,
                              ItemCodec codec, Function<UUID, String> nameLookup, Supplier<ImportSettings> settings,
                              Clock clock, Executor asyncExecutor, Executor syncExecutor, Runnable refreshCaches,
                              Consumer<Event> eventCaller, Function<String, Plugin> pluginLookup,
                              File pluginsFolder, File reportsFolder) {
    }

    private final Environment environment;

    private final AtomicReference<ImportRun> running = new AtomicReference<>();

    private volatile ImportReport lastReport;

    public ImportManager(Environment environment) {
        this.environment = environment;
    }

    public static ImportManager create(FAuction plugin, ImporterRegistryImpl registry, ImportStore store) {
        File pluginsFolder = plugin.getDataFolder().getAbsoluteFile().getParentFile();
        return new ImportManager(new Environment(
                plugin.getLogger(),
                plugin.getConfigurationManager().getDatabase().getSqlType(),
                registry,
                store,
                ItemCodec.SERVER,
                uuid -> Bukkit.getOfflinePlayer(uuid).getName(),
                () -> ImportSettings.from(plugin.getConfigurationManager().getGlobalConfig()),
                Clock.systemDefaultZone(),
                task -> FAuction.getFoliaLib().getScheduler().runAsync(wrapped -> task.run()),
                task -> FAuction.getFoliaLib().getScheduler().runNextTick(wrapped -> task.run()),
                () -> {
                    plugin.getAuctionCommandManager().updateCache();
                    plugin.getExpireCommandManager().updateCache();
                    plugin.getHistoricCommandManager().updateCache();
                },
                event -> Bukkit.getPluginManager().callEvent(event),
                name -> Bukkit.getPluginManager().getPlugin(name),
                pluginsFolder,
                new File(plugin.getDataFolder(), "imports")));
    }

    /**
     * Runs {@code task} off the main thread, on the executor of the imports : for the commands that
     * read the sources (availability checks).
     */
    public void runAsync(Runnable task) {
        environment.asyncExecutor().execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                environment.logger().log(Level.SEVERE, "Unhandled exception in an import command", e);
            }
        });
    }

    public ImporterRegistryImpl registry() {
        return environment.registry();
    }

    public boolean isSqliteRefused() {
        return environment.sqlType() == SQLType.SQLite;
    }

    /**
     * Starts an import. The availability of the module is checked on the import thread : an
     * unavailable module ends with a {@link ImportReport.Status#UNAVAILABLE} report.
     *
     * @param onFinished called with the report, from the import thread.
     */
    public StartResult start(String importerId, ImportOptions options, Consumer<ImportReport> onFinished) {
        if (isSqliteRefused()) {
            return StartResult.REFUSED_SQLITE;
        }

        Optional<DataImporter> found = environment.registry().find(importerId);
        if (found.isEmpty()) {
            return StartResult.UNKNOWN_IMPORTER;
        }
        DataImporter importer = found.get();

        Set<ImportDataType> types = EnumSet.noneOf(ImportDataType.class);
        types.addAll(supportedTypes(importer));
        if (!options.types().isEmpty()) {
            types.retainAll(options.types());
        }
        if (types.isEmpty()) {
            return StartResult.NO_TYPE;
        }

        ImportSettings settings = environment.settings().get();
        ImportValidator validator = new ImportValidator(settings, options, environment.codec(), environment.nameLookup(), environment.clock());
        ImportRun run = new ImportRun(importer.id(), types, options, validator, environment.store(), settings,
                environment.clock(), new ImportContextImpl.PrefixedLogger(environment.logger(), "[Import:" + importer.id() + "] "));

        if (!running.compareAndSet(null, run)) {
            return StartResult.ALREADY_RUNNING;
        }

        ImportStartEvent startEvent = new ImportStartEvent(importer.id(), types, options.dryRun());
        try {
            environment.eventCaller().accept(startEvent);
        } catch (RuntimeException e) {
            environment.logger().log(Level.WARNING, "A listener of ImportStartEvent failed", e);
        }
        if (startEvent.isCancelled()) {
            running.set(null);
            run.markDone();
            return StartResult.CANCELLED_BY_EVENT;
        }

        try {
            environment.asyncExecutor().execute(() -> {
                try {
                    execute(importer, run, onFinished);
                } finally {
                    running.compareAndSet(run, null);
                    run.markDone();
                }
            });
        } catch (RuntimeException e) {
            running.compareAndSet(run, null);
            run.markDone();
            throw e;
        }
        return StartResult.STARTED;
    }

    private void execute(DataImporter importer, ImportRun run, Consumer<ImportReport> onFinished) {
        Logger logger = environment.logger();
        ImportContext context = context(importer.id(), run.options(), run.types());

        ImportReport.Status status;
        String failure = null;

        ImportAvailability availability;
        try {
            availability = importer.checkAvailability(context);
        } catch (Throwable t) {
            availability = ImportAvailability.unavailable("Availability check failed : " + t);
        }

        if (run.isCancelled()) {
            // Cancelled before its thread started (the server is stopping) : nothing is read.
            status = ImportReport.Status.CANCELLED;
        } else if (availability == null || !availability.available()) {
            status = ImportReport.Status.UNAVAILABLE;
            failure = availability == null ? "The module gave no availability" : availability.message();
        } else {
            logger.info("Import " + importer.id() + " started" + (run.options().dryRun() ? " (dry run, nothing will be written)" : "")
                    + ", types " + run.types());
            try {
                importer.read(context, run);
                status = run.isCancelled() ? ImportReport.Status.CANCELLED : ImportReport.Status.COMPLETED;
            } catch (Throwable t) {
                // A module is third party code : whatever it throws ends the import cleanly.
                logger.log(Level.SEVERE, "Import " + importer.id() + " failed while reading its source", t);
                status = ImportReport.Status.FAILED;
                failure = String.valueOf(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
            }

            // The rows validated before the end (or the cancellation, or the failure) : the batch in
            // progress is always finished.
            run.flush();

            if (!run.options().dryRun()) {
                try {
                    environment.refreshCaches().run();
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Could not refresh the caches after the import, they will be on the next cache update", e);
                }
            }
        }

        ImportReport report = run.report(status, failure, null);
        File logFile = null;
        try {
            logFile = ImportReportWriter.write(environment.reportsFolder(), report, run.progressLine(), environment.clock().getZone());
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not write the report of the import", e);
        }
        if (logFile != null) {
            report = run.report(status, failure, logFile);
        }
        // Kept for /ah admin import status : the counters only, the refused rows are in the file.
        lastReport = run.report(status, failure, logFile, false);

        logger.info("Import " + importer.id() + " finished : " + report.status() + ". " + run.progressLine()
                + (logFile != null ? ". Report : " + logFile.getPath() : ""));

        try {
            environment.eventCaller().accept(new ImportFinishEvent(report));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "A listener of ImportFinishEvent failed", e);
        }
        try {
            onFinished.accept(report);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not send the report of the import", e);
        }
    }

    /**
     * The context a module sees, for an import or for {@code /ah admin import list}.
     */
    public ImportContext context(String importerId, ImportOptions options, Set<ImportDataType> types) {
        return new ImportContextImpl(importerId, environment.logger(), options, types, environment.clock(),
                environment.pluginsFolder(), environment.pluginLookup(),
                new ImportHelpersImpl(environment.pluginsFolder(), environment.nameLookup(), environment.syncExecutor(),
                        environment.codec(), Bukkit::isPrimaryThread));
    }

    /**
     * Asks a module whether it can run, never throwing.
     */
    public ImportAvailability checkAvailability(DataImporter importer) {
        return checkAvailability(importer, ImportOptions.DEFAULTS);
    }

    /**
     * Asks a module whether it can run with these options (its {@code --opt} may tell it where its
     * source is), never throwing.
     */
    public ImportAvailability checkAvailability(DataImporter importer, ImportOptions options) {
        try {
            ImportAvailability availability = importer.checkAvailability(context(importer.id(), options, supportedTypes(importer)));
            return availability == null ? ImportAvailability.unavailable("The module gave no availability") : availability;
        } catch (Throwable t) {
            return ImportAvailability.unavailable("Availability check failed : " + t);
        }
    }

    /**
     * The types of a module, never throwing : a third party module failing here supports nothing.
     */
    public static Set<ImportDataType> supportedTypes(DataImporter importer) {
        Set<ImportDataType> supported;
        try {
            supported = importer.supportedTypes();
        } catch (RuntimeException e) {
            return EnumSet.noneOf(ImportDataType.class);
        }
        return supported == null || supported.isEmpty() ? EnumSet.noneOf(ImportDataType.class) : EnumSet.copyOf(supported);
    }

    public Optional<ImportRun> current() {
        return Optional.ofNullable(running.get());
    }

    public Optional<ImportReport> lastReport() {
        return Optional.ofNullable(lastReport);
    }

    /**
     * @return whether an import was running.
     */
    public boolean cancel() {
        ImportRun run = running.get();
        if (run == null) {
            return false;
        }
        run.cancel();
        return true;
    }

    /**
     * Waits for the running import to finish, if any.
     *
     * @return whether it finished in time.
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        ImportRun run = running.get();
        return run == null || run.awaitDone(timeout, unit);
    }

    /**
     * Cancels the running import and waits for its batch in progress, before the database is closed.
     */
    public void shutdown() {
        if (!cancel()) {
            return;
        }
        environment.logger().warning("The server is stopping : the running import is cancelled, run it again to finish it.");
        try {
            if (!awaitCompletion(30, TimeUnit.SECONDS)) {
                environment.logger().severe("The import did not stop in time ; its last batch may have been interrupted (it was rolled back if so).");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
