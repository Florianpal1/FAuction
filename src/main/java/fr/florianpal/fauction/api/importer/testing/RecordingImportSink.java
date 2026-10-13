package fr.florianpal.fauction.api.importer.testing;

import fr.florianpal.fauction.api.importer.ImportSink;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedPendingCurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A sink that only records what a module pushes, to test a module without FAuction nor a database :
 * <pre>{@code
 * RecordingImportSink sink = new RecordingImportSink();
 * new MyImporter().read(context, sink);
 * assertEquals(3, sink.auctions().size());
 * }</pre>
 * No validation is done here : what is recorded is exactly what the module produced.
 */
public class RecordingImportSink implements ImportSink {

    /**
     * A row skipped by the module.
     */
    public record Skip(String sourceId, String reason) {
    }

    private final List<ImportedAuction> auctions = new ArrayList<>();

    private final List<ImportedExpired> expired = new ArrayList<>();

    private final List<ImportedHistoric> historics = new ArrayList<>();

    private final List<ImportedPendingCurrency> pendingCurrencies = new ArrayList<>();

    private final List<Skip> skips = new ArrayList<>();

    private volatile boolean cancelled;

    private int cancelAfter = -1;

    @Override
    public synchronized void accept(ImportedAuction auction) {
        auctions.add(auction);
        countDown();
    }

    @Override
    public synchronized void accept(ImportedExpired expired) {
        this.expired.add(expired);
        countDown();
    }

    @Override
    public synchronized void accept(ImportedHistoric historic) {
        historics.add(historic);
        countDown();
    }

    @Override
    public synchronized void accept(ImportedPendingCurrency pendingCurrency) {
        pendingCurrencies.add(pendingCurrency);
        countDown();
    }

    @Override
    public synchronized void skip(String sourceId, String reason) {
        skips.add(new Skip(sourceId, reason));
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    /**
     * Cancels by itself once {@code rows} rows have been accepted, to test the cancellation of a
     * module.
     */
    public synchronized void cancelAfter(int rows) {
        this.cancelAfter = rows;
        if (rows <= 0) {
            cancelled = true;
        }
    }

    private void countDown() {
        if (cancelAfter > 0 && --cancelAfter == 0) {
            cancelled = true;
        }
    }

    public synchronized List<ImportedAuction> auctions() {
        return Collections.unmodifiableList(new ArrayList<>(auctions));
    }

    public synchronized List<ImportedExpired> expired() {
        return Collections.unmodifiableList(new ArrayList<>(expired));
    }

    public synchronized List<ImportedHistoric> historics() {
        return Collections.unmodifiableList(new ArrayList<>(historics));
    }

    public synchronized List<ImportedPendingCurrency> pendingCurrencies() {
        return Collections.unmodifiableList(new ArrayList<>(pendingCurrencies));
    }

    public synchronized List<Skip> skips() {
        return Collections.unmodifiableList(new ArrayList<>(skips));
    }

    /**
     * Every row accepted, all types together.
     */
    public synchronized int size() {
        return auctions.size() + expired.size() + historics.size() + pendingCurrencies.size();
    }
}
