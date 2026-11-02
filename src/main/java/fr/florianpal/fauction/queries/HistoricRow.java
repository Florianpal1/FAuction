package fr.florianpal.fauction.queries;

import java.util.UUID;

/**
 * A row of {@code fa_auctions_historic}.
 *
 * @param date    listing date, milliseconds since the epoch.
 * @param buyDate date of the sale, milliseconds since the epoch, {@code null} when unknown.
 */
public record HistoricRow(UUID playerUuid, String playerName, UUID playerBuyerUuid, String playerBuyerName,
                          byte[] item, double price, long date, Long buyDate) {
}
