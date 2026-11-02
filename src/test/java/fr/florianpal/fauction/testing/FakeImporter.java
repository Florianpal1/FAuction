package fr.florianpal.fauction.testing;

import fr.florianpal.fauction.api.importer.DataImporter;
import fr.florianpal.fauction.api.importer.ImportAvailability;
import fr.florianpal.fauction.api.importer.ImportContext;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportSink;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedPendingCurrency;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A scriptable module : the rows to push, an exception after k rows, a pause to hold the import
 * while a test looks at it.
 */
public class FakeImporter implements DataImporter {

    private final String id;

    private final List<Object> rows = new ArrayList<>();

    private Set<ImportDataType> types = EnumSet.allOf(ImportDataType.class);

    private ImportAvailability availability = ImportAvailability.available("fake source");

    private int failAfter = -1;

    private int requiredApiVersion = 1;

    private CountDownLatch pause;

    private CountDownLatch paused;

    private int pauseAfter = -1;

    private Consumer<ImportSink> afterRows;

    public int reads;

    public FakeImporter(String id) {
        this.id = id;
    }

    public FakeImporter rows(Object... rows) {
        this.rows.addAll(List.of(rows));
        return this;
    }

    public FakeImporter rows(List<?> rows) {
        this.rows.addAll(rows);
        return this;
    }

    public FakeImporter types(Set<ImportDataType> types) {
        this.types = types;
        return this;
    }

    public FakeImporter availability(ImportAvailability availability) {
        this.availability = availability;
        return this;
    }

    /**
     * Throws once {@code rows} rows have been pushed.
     */
    public FakeImporter failAfter(int rows) {
        this.failAfter = rows;
        return this;
    }

    public FakeImporter requiredApiVersion(int version) {
        this.requiredApiVersion = version;
        return this;
    }

    /**
     * Waits on {@code release} once {@code rows} rows have been pushed, counting {@code reached}
     * down first.
     */
    public FakeImporter pauseAfter(int rows, CountDownLatch reached, CountDownLatch release) {
        this.pauseAfter = rows;
        this.paused = reached;
        this.pause = release;
        return this;
    }

    public FakeImporter afterRows(Consumer<ImportSink> afterRows) {
        this.afterRows = afterRows;
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return "Fake " + id;
    }

    @Override
    public Set<ImportDataType> supportedTypes() {
        return types;
    }

    @Override
    public ImportAvailability checkAvailability(ImportContext context) {
        return availability;
    }

    @Override
    public int requiredApiVersion() {
        return requiredApiVersion;
    }

    @Override
    public void read(ImportContext context, ImportSink sink) throws Exception {
        reads++;
        int pushed = 0;
        for (Object row : rows) {
            if (sink.isCancelled()) {
                return;
            }
            if (pushed == failAfter) {
                throw new IllegalStateException("Source broken after " + pushed + " rows");
            }
            if (pushed == pauseAfter) {
                paused.countDown();
                if (!pause.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Never released");
                }
            }
            switch (row) {
                case ImportedAuction auction -> sink.accept(auction);
                case ImportedExpired expired -> sink.accept(expired);
                case ImportedHistoric historic -> sink.accept(historic);
                case ImportedPendingCurrency pending -> sink.accept(pending);
                case String skip -> sink.skip(skip, "skipped by the fake");
                default -> throw new IllegalArgumentException(String.valueOf(row));
            }
            pushed++;
        }
        if (afterRows != null) {
            afterRows.accept(sink);
        }
    }
}
