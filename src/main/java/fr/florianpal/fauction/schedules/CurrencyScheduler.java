package fr.florianpal.fauction.schedules;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.objects.CurrencyPending;
import fr.florianpal.fauction.queries.CurrencyPendingQueries;
import fr.florianpal.fauction.utils.CurrencyUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CurrencyScheduler implements Runnable {

    private final CurrencyPendingQueries currencyPendingQueries;

    private final FAuction plugin;

    public CurrencyScheduler(FAuction plugin) {
        this.plugin = plugin;
        this.currencyPendingQueries = plugin.getCurrencyPendingQueries();
    }

    @Override
    public void run() {
        FAuction.newChain().asyncFirst(currencyPendingQueries::getCurrencyPending).asyncLast(currencyPendings -> {

            for (CurrencyPending currencyPending : currencyPendings) {

                Player player = Bukkit.getPlayer(currencyPending.getPlayerUUID());

                // Still offline : the row stays pending for a later pass.
                if (player == null) {
                    continue;
                }

                // The payment goes through the region thread of the player : the EXPERIENCE and
                // LEVEL modes write to the player themselves, which the global region may not do
                // under Folia. The row is only cleared once the payment is confirmed, so a refused
                // deposit, or a player who left before being paid (the sync step is then skipped and
                // given is null), leaves the money owed instead of losing it.
                FAuction.newChain(player)
                        .syncFirst(() -> CurrencyUtil.giveCurrency(plugin, player, currencyPending.getCurrencyType(), currencyPending.getAmount()))
                        .asyncLast(given -> {
                            if (Boolean.TRUE.equals(given)) {
                                currencyPendingQueries.deleteCurrencyPending(currencyPending.getId());
                            }
                        })
                        .execute();
            }

        }).execute();
    }
}
