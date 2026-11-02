package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportReport;
import fr.florianpal.fauction.api.importer.ImportSink;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedPendingCurrency;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One import being run : the sink handed to the module, the buffer of validated rows, and the
 * counters {@code /ah admin import status} reads while it runs.
 * <p>
 * {@link #accept} and {@link #skip} are called from the import thread only ; the counters and the
 * cancellation flag are read and set from any thread.
 */
public class ImportRun implements ImportSink {

    /**
     * Above this, the issues are only counted : the detailed file of a run with millions of refused
     * rows must not exhaust the memory of the server.
     */
    static final int MAX_ISSUES_KEPT = 100_000;

    static final int MAX_ISSUE_TEXT = 300;

    private static final class Counters {
        final AtomicLong read = new AtomicLong();
        final AtomicLong imported = new AtomicLong();
        final AtomicLong alreadyImported = new AtomicLong();
        final AtomicLong rejected = new AtomicLong();
        final AtomicLong failed = new AtomicLong();
        final AtomicLong movedToExpires = new AtomicLong();

        ImportReport.TypeCounts snapshot() {
            return new ImportReport.TypeCounts(read.get(), imported.get(), alreadyImported.get(), rejected.get(), failed.get(), movedToExpires.get());
        }
    }

    private final String importerId;

    private final Set<ImportDataType> types;

    private final ImportOptions options;

    private final ImportValidator validator;

    private final ImportStore store;

    private final int batchSize;

    private final Clock clock;

    private final Logger logger;

    private final long progressIntervalMillis;

    private final Instant startedAt;

    private final Map<ImportDataType, Counters> counters = new EnumMap<>(ImportDataType.class);

    private final AtomicLong skipped = new AtomicLong();

    private final AtomicLong issueCount = new AtomicLong();

    private final AtomicLong batchErrors = new AtomicLong();

    private final List<ImportReport.Issue> issues = Collections.synchronizedList(new ArrayList<>());

    private final List<PreparedRow> buffer = new ArrayList<>();

    /**
     * The rows accepted by this run : a module pushing the same sourceId twice gets the second one
     * refused, instead of a whole batch failing on the unique constraint of the journal.
     */
    private final Set<String> seen = new HashSet<>();

    private volatile boolean cancelled;

    private long lastProgressLog;

    public ImportRun(String importerId, Set<ImportDataType> types, ImportOptions options, ImportValidator validator,
                     ImportStore store, ImportSettings settings, Clock clock, Logger logger) {
        this.importerId = importerId;
        this.types = Set.copyOf(types);
        this.options = options;
        this.validator = validator;
        this.store = store;
        this.batchSize = settings.batchSize();
        this.clock = clock;
        this.logger = logger;
        this.progressIntervalMillis = settings.progressIntervalSeconds() * 1000L;
        this.startedAt = clock.instant();
        this.lastProgressLog = clock.millis();
        for (ImportDataType type : types) {
            counters.put(type, new Counters());
        }
    }

    public String importerId() {
        return importerId;
    }

    public ImportOptions options() {
        return options;
    }

    public Set<ImportDataType> types() {
        return types;
    }

    @Override
    public void accept(ImportedAuction auction) {
        handle(ImportDataType.AUCTION, auction == null ? null : auction.sourceId(), () -> validator.validate(auction));
    }

    @Override
    public void accept(ImportedExpired expired) {
        handle(ImportDataType.EXPIRED, expired == null ? null : expired.sourceId(), () -> validator.validate(expired));
    }

    @Override
    public void accept(ImportedHistoric historic) {
        handle(ImportDataType.HISTORIC, historic == null ? null : historic.sourceId(), () -> validator.validate(historic));
    }

    @Override
    public void accept(ImportedPendingCurrency pendingCurrency) {
        handle(ImportDataType.PENDING_CURRENCY, pendingCurrency == null ? null : pendingCurrency.sourceId(), () -> validator.validate(pendingCurrency));
    }

    @Override
    public void skip(String sourceId, String reason) {
        if (cancelled) {
            return;
        }
        skipped.incrementAndGet();
        issue(ImportReport.IssueKind.SKIPPED, null, sourceId, reason);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    /**
     * Created with the run, so whoever cancels it can wait for it, even before its thread started.
     */
    private final CountDownLatch done = new CountDownLatch(1);

    void markDone() {
        done.countDown();
    }

    boolean awaitDone(long timeout, TimeUnit unit) throws InterruptedException {
        return done.await(timeout, unit);
    }

    private void handle(ImportDataType type, String sourceId, Supplier<ImportValidator.Result> validation) {
        // Once cancelled, nothing more is taken : the batch in progress is written by the last
        // flush(), no other one after it.
        if (cancelled) {
            return;
        }
        Counters typeCounters = counters.get(type);
        if (typeCounters == null) {
            // A type the administrator did not ask for (--types).
            return;
        }
        typeCounters.read.incrementAndGet();

        if (sourceId == null) {
            reject(typeCounters, type, "?", "Null row");
            return;
        }

        ImportValidator.Result result;
        try {
            result = validation.get();
        } catch (RuntimeException e) {
            // The validation of a row must never stop the import, whatever a module hands in.
            result = ImportValidator.Result.rejected("Invalid row : " + e);
        }

        if (result.isRejected()) {
            reject(typeCounters, type, sourceId, result.rejection());
        } else if (!seen.add(result.row().key())) {
            reject(typeCounters, type, sourceId, "Same sourceId pushed twice by the module");
        } else {
            buffer.add(result.row());
            if (buffer.size() >= batchSize) {
                flush();
            }
        }

        logProgress();
    }

    private void reject(Counters typeCounters, ImportDataType type, String sourceId, String reason) {
        typeCounters.rejected.incrementAndGet();
        issue(ImportReport.IssueKind.REJECTED, type, sourceId, reason);
    }

    private void issue(ImportReport.IssueKind kind, ImportDataType type, String sourceId, String reason) {
        issueCount.incrementAndGet();
        if (issues.size() < MAX_ISSUES_KEPT) {
            issues.add(new ImportReport.Issue(kind, type, bounded(sourceId), bounded(reason)));
        }
    }

    /**
     * The identifiers and reasons come from the files of the source : bounded in size, and without
     * control characters that would forge lines in the detailed report.
     */
    static String bounded(String text) {
        String value = String.valueOf(text).replaceAll("\\p{Cntrl}", " ");
        return value.length() > MAX_ISSUE_TEXT ? value.substring(0, MAX_ISSUE_TEXT) + "..." : value;
    }

    /**
     * Writes the rows waiting in the buffer, as one transaction. A batch the database refuses is
     * counted as failed, row by row, and the next ones carry on.
     */
    void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        List<PreparedRow> batch = new ArrayList<>(buffer);
        buffer.clear();

        try {
            count(batch, store.write(importerId, batch, options.forceReimport(), options.dryRun()));
        } catch (Exception e) {
            batchErrors.incrementAndGet();
            logger.log(Level.WARNING, "A batch of " + batch.size() + " rows was refused by the database, nothing of it was written ; "
                    + (batch.size() > 1 ? "retrying its rows one by one" : "row failed"), e);
            if (batch.size() == 1) {
                fail(batch.get(0), e);
                return;
            }
            // One bad row must not take the whole batch down with it at every run : each row is
            // written alone, in its own transaction, and only the rows really refused fail.
            for (PreparedRow row : batch) {
                try {
                    count(List.of(row), store.write(importerId, List.of(row), options.forceReimport(), options.dryRun()));
                } catch (Exception rowError) {
                    fail(row, rowError);
                }
            }
        }
    }

    private void fail(PreparedRow row, Exception error) {
        counters.get(row.sourceType()).failed.incrementAndGet();
        issue(ImportReport.IssueKind.FAILED, row.sourceType(), row.sourceId(), "Refused by the database : " + error.getMessage());
    }

    private void count(List<PreparedRow> batch, Set<String> alreadyImported) {
        for (PreparedRow row : batch) {
            Counters typeCounters = counters.get(row.sourceType());
            if (alreadyImported.contains(row.key())) {
                typeCounters.alreadyImported.incrementAndGet();
            } else {
                typeCounters.imported.incrementAndGet();
                if (row.movedToExpires()) {
                    typeCounters.movedToExpires.incrementAndGet();
                }
            }
        }
    }

    private void logProgress() {
        if (progressIntervalMillis <= 0) {
            return;
        }
        long now = clock.millis();
        if (now - lastProgressLog >= progressIntervalMillis) {
            lastProgressLog = now;
            logger.info("Progress : " + progressLine());
        }
    }

    /**
     * One line for the console and {@code /ah admin import status}.
     */
    public String progressLine() {
        StringBuilder line = new StringBuilder();
        for (Map.Entry<ImportDataType, Counters> entry : counters.entrySet()) {
            ImportReport.TypeCounts counts = entry.getValue().snapshot();
            if (!line.isEmpty()) {
                line.append(" | ");
            }
            line.append(entry.getKey().id()).append(' ')
                    .append(counts.read()).append(" read, ")
                    .append(counts.imported()).append(" imported, ")
                    .append(counts.alreadyImported()).append(" already, ")
                    .append(counts.rejected() + counts.failed()).append(" refused");
        }
        if (skipped.get() > 0) {
            line.append(" | ").append(skipped.get()).append(" skipped by the module");
        }
        return line.toString();
    }

    public ImportReport.TypeCounts counts(ImportDataType type) {
        Counters typeCounters = counters.get(type);
        return typeCounters == null ? ImportReport.TypeCounts.EMPTY : typeCounters.snapshot();
    }

    public long batchErrors() {
        return batchErrors.get();
    }

    public Instant startedAt() {
        return startedAt;
    }

    ImportReport report(ImportReport.Status status, String failure, File logFile) {
        return report(status, failure, logFile, true);
    }

    ImportReport report(ImportReport.Status status, String failure, File logFile, boolean withIssues) {
        Map<ImportDataType, ImportReport.TypeCounts> snapshot = new EnumMap<>(ImportDataType.class);
        counters.forEach((type, typeCounters) -> snapshot.put(type, typeCounters.snapshot()));
        List<ImportReport.Issue> issueCopy = List.of();
        if (withIssues) {
            synchronized (issues) {
                issueCopy = new ArrayList<>(issues);
            }
        }
        return new ImportReport(importerId, options.dryRun(), status, startedAt, clock.instant(), snapshot,
                skipped.get(), issueCopy, issueCount.get(), failure, logFile);
    }
}
