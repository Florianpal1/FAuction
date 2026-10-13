package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.enums.CurrencyType;

import java.util.UUID;

/**
 * A row of {@code fa_currency_pending}.
 */
public record CurrencyPendingRow(UUID playerUuid, CurrencyType currencyType, double amount) {
}
