package fr.florianpal.fauction.managers.importer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The second typing a real import needs : {@code /ah admin import run <id> confirm}, by the same
 * sender, for the same module, within {@link #DELAY}. The options are the ones of the first typing,
 * so what is confirmed is exactly what was shown.
 */
public class ImportConfirmations {

    public static final Duration DELAY = Duration.ofSeconds(30);

    private record Pending(String importerId, ImportOptions options, Instant expiresAt) {
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private final Clock clock;

    public ImportConfirmations(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param senderKey identifies the sender : the UUID of a player, the name of the console.
     */
    public void request(String senderKey, String importerId, ImportOptions options) {
        pending.put(senderKey, new Pending(importerId, options.withoutConfirm(), clock.instant().plus(DELAY)));
    }

    /**
     * Consumes the pending request of {@code senderKey}.
     *
     * @return the options to run with, empty if there is nothing to confirm (none, expired, or for
     * another module).
     */
    public Optional<ImportOptions> confirm(String senderKey, String importerId) {
        Pending request = pending.remove(senderKey);
        if (request == null || !request.importerId().equals(importerId) || clock.instant().isAfter(request.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(request.options());
    }
}
