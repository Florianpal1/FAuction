package fr.florianpal.fauction.queries;

import java.util.UUID;

/**
 * A row of {@code auctions} or {@code expires}, the two tables sharing their columns.
 *
 * @param date milliseconds since the epoch, as the {@code date} column stores it.
 */
public record ItemRow(UUID playerUuid, String playerName, byte[] item, double price, long date) {
}
