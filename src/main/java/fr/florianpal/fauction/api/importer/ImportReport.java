package fr.florianpal.fauction.api.importer;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The outcome of an import : what was read, imported, ignored and refused, and why. Immutable.
 * <p>
 * For every data type, {@code read = imported + alreadyImported + rejected + failed} : no row read
 * disappears without being counted.
 * <p>
 * Built by FAuction only : its constructors are not part of the stable API, its accessors are.
 */
public final class ImportReport {

    public enum Status {

        /**
         * The source was read to the end.
         */
        COMPLETED,

        /**
         * Cancelled by an administrator : the batch being written was finished, nothing after it.
         */
        CANCELLED,

        /**
         * The module failed to read the source ({@link #failure()}), the rows pushed before were
         * still imported.
         */
        FAILED,

        /**
         * The module refused to run ({@link DataImporter#checkAvailability}), nothing was read.
         */
        UNAVAILABLE
    }

    /**
     * @param read            rows pushed by the module, for the requested types.
     * @param imported        rows written (or that would have been, in a dry run).
     * @param alreadyImported rows a previous import already brought, ignored.
     * @param rejected        rows refused by the validation, see {@link #issues()}.
     * @param failed          valid rows of a batch the database refused, see {@link #issues()}.
     * @param movedToExpires  among {@code imported}, the sales given back to their seller instead of
     *                        being put on sale (options {@code --expired-to-expires},
     *                        {@code --other-currency=expire}, {@code --apply-limits}).
     */
    public record TypeCounts(long read, long imported, long alreadyImported, long rejected, long failed,
                             long movedToExpires) {

        public static final TypeCounts EMPTY = new TypeCounts(0, 0, 0, 0, 0, 0);
    }

    public enum IssueKind {

        /**
         * Refused by the validation of FAuction.
         */
        REJECTED,

        /**
         * Skipped by the module itself, {@link ImportSink#skip}.
         */
        SKIPPED,

        /**
         * Part of a batch the database refused.
         */
        FAILED
    }

    /**
     * @param type     the data type of the row, {@code null} for a row skipped by the module.
     * @param sourceId the identifier of the row in the source.
     */
    public record Issue(IssueKind kind, @Nullable ImportDataType type, String sourceId, String reason) {
    }

    private final String importerId;

    private final boolean dryRun;

    private final Status status;

    private final Instant startedAt;

    private final Instant finishedAt;

    private final Map<ImportDataType, TypeCounts> counts;

    private final long skipped;

    private final List<Issue> issues;

    private final long issueCount;

    private final String failure;

    private final File logFile;

    public ImportReport(String importerId, boolean dryRun, Status status, Instant startedAt, Instant finishedAt,
                        Map<ImportDataType, TypeCounts> counts, long skipped, List<Issue> issues, long issueCount,
                        @Nullable String failure, @Nullable File logFile) {
        this.importerId = Objects.requireNonNull(importerId, "importerId");
        this.dryRun = dryRun;
        this.status = Objects.requireNonNull(status, "status");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
        Map<ImportDataType, TypeCounts> copy = new EnumMap<>(ImportDataType.class);
        copy.putAll(counts);
        this.counts = Collections.unmodifiableMap(copy);
        this.skipped = skipped;
        this.issues = List.copyOf(issues);
        this.issueCount = issueCount;
        this.failure = failure;
        this.logFile = logFile;
    }

    public String importerId() {
        return importerId;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public Status status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    /**
     * The counters of every requested type.
     */
    public Map<ImportDataType, TypeCounts> counts() {
        return counts;
    }

    public TypeCounts counts(ImportDataType type) {
        return counts.getOrDefault(type, TypeCounts.EMPTY);
    }

    /**
     * Rows the module skipped itself, {@link ImportSink#skip}.
     */
    public long skipped() {
        return skipped;
    }

    /**
     * The rows refused, skipped or failed, with their reason. Bounded in memory : {@link #issueCount()}
     * gives the total.
     */
    public List<Issue> issues() {
        return issues;
    }

    public long issueCount() {
        return issueCount;
    }

    public long totalImported() {
        return counts.values().stream().mapToLong(TypeCounts::imported).sum();
    }

    /**
     * Why the module stopped, for {@link Status#FAILED} and {@link Status#UNAVAILABLE}.
     */
    public @Nullable String failure() {
        return failure;
    }

    /**
     * The detailed report written in {@code plugins/FAuction/imports/}, if it could be written.
     */
    public @Nullable File logFile() {
        return logFile;
    }
}
