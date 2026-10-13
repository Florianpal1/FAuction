package fr.florianpal.fauction.api.importer;

import java.util.Objects;

/**
 * Whether a module can run right now, with a message for the administrator : where the data was
 * found, or why it cannot be read (source plugin still enabled, files missing...).
 *
 * @param available whether the import can be run.
 * @param message   what to tell the administrator, never null.
 */
public record ImportAvailability(boolean available, String message) {

    public ImportAvailability {
        Objects.requireNonNull(message, "message");
    }

    public static ImportAvailability available(String message) {
        return new ImportAvailability(true, message);
    }

    public static ImportAvailability unavailable(String reason) {
        return new ImportAvailability(false, reason);
    }
}
