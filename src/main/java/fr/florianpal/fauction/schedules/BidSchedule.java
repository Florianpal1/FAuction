package fr.florianpal.fauction.schedules;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.events.BidWonEvent;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Bid;
import fr.florianpal.fauction.utils.CurrencyUtil;
import fr.florianpal.fauction.utils.FormatUtil;
import fr.florianpal.fauction.utils.MessageUtil;
import fr.florianpal.fauction.utils.SerializationUtil;
import fr.florianpal.fauction.languages.MessageKeys;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class BidSchedule implements Runnable {

    private final FAuction plugin;

    private List<Bid> bids = new ArrayList<>();

    public BidSchedule(FAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        FAuction.newChain().asyncFirst(() -> plugin.getBidCommandManager().getBids()).syncLast(bidList -> {
            this.bids = bidList;
            Date now = Calendar.getInstance().getTime();

            for (Bid bid : this.bids) {
                if (isExpired(bid.getEndDate(), now)) {
                    // Reserve first : a bid raised in the meantime must not be dropped on the floor,
                    // and the row's last state (bidder or not) is exactly what claim() hands back.
                    Bid ended = plugin.getBidCommandManager().claim(bid.getId());
                    if (ended == null) {
                        continue;
                    }

                    if (ended.hasBidder()) {
                        finalizeWithWinner(ended);
                    } else {
                        finalizeWithoutWinner(ended);
                    }
                }
            }
        }).execute();
    }

    /**
     * The winner already paid at bid time (escrow). The item is handed over (or parked in the
     * expired items if the winner is offline) BEFORE the seller is paid : delivery is the step that
     * can fail (a database write, if the winner is offline), and restore() puts the bid back with its
     * end date unchanged, which makes the very next tick retry the exact same finalization. Paying
     * the seller only once delivery is confirmed means that retry can never pay them twice.
     */
    private void finalizeWithWinner(Bid bid) {

        OfflinePlayer seller = Bukkit.getOfflinePlayer(bid.getSellerUuid());
        OfflinePlayer winner = Bukkit.getOfflinePlayer(bid.getCurrentBidderUuid());

        if (winner.isOnline()) {
            if (winner.getPlayer().getInventory().firstEmpty() == -1) {
                winner.getPlayer().getWorld().dropItem(winner.getPlayer().getLocation(), bid.getItemStack());
            } else {
                winner.getPlayer().getInventory().addItem(bid.getItemStack());
            }
        } else if (!plugin.getExpireCommandManager().addExpire(new Auction(0, winner.getUniqueId(), bid.getCurrentBidderName(), bid.getCurrentPrice(), SerializationUtil.serialize(bid.getItemStack()), bid.getEndDate().getTime()))) {
            plugin.getLogger().severe("Item of the bid won by " + bid.getCurrentBidderName() + " could not be moved to the expired items ; bid put back on the market instead of being lost, seller not paid yet.");
            plugin.getBidCommandManager().restore(bid);
            return;
        }

        if (!CurrencyUtil.giveCurrency(plugin, seller, plugin.getConfigurationManager().getGlobalConfig().getCurrencyType(), bid.getCurrentPrice())) {
            // The item is already gone at this point (delivered or parked for the winner) ; there is
            // no transaction spanning both tables, so this failure is reported for manual correction
            // instead of being retried (retrying would only repeat the same delivery, not the payment).
            plugin.getLogger().severe("Payment of " + bid.getSellerName() + " failed for the bid on item " + FormatUtil.titleItemFormat(bid.getItemStack()) + " (winner " + bid.getCurrentBidderName() + " already received the item) ; manual correction required.");
        }

        plugin.getBidHistoricCommandManager().addBidHistoric(bid, bid.getCurrentBidderUuid(), bid.getCurrentBidderName());

        if (seller.isOnline()) {
            MessageUtil.sendMessage(plugin, seller.getPlayer(), MessageKeys.BID_SOLD, "{item}", FormatUtil.titleItemFormat(bid.getItemStack()), "{price}", String.valueOf(bid.getCurrentPrice()));
        }
        if (winner.isOnline()) {
            MessageUtil.sendMessage(plugin, winner.getPlayer(), MessageKeys.BID_WON, "{item}", FormatUtil.titleItemFormat(bid.getItemStack()), "{price}", String.valueOf(bid.getCurrentPrice()));
        }

        Bukkit.getPluginManager().callEvent(new BidWonEvent(winner, bid));
    }

    /**
     * No bid was ever placed : the item goes back to the seller through the same expired-items
     * mechanism used for a classic auction that expired unsold.
     */
    private void finalizeWithoutWinner(Bid bid) {

        Auction asExpire = new Auction(0, bid.getSellerUuid(), bid.getSellerName(), bid.getStartPrice(), SerializationUtil.serialize(bid.getItemStack()), bid.getEndDate().getTime());

        if (!plugin.getExpireCommandManager().addExpire(asExpire)) {
            plugin.getLogger().severe("Bid " + bid.getId() + " of " + bid.getSellerName() + " could not be moved to the expired items ; put back on the market instead of being lost.");
            plugin.getBidCommandManager().restore(bid);
            return;
        }

        OfflinePlayer seller = Bukkit.getOfflinePlayer(bid.getSellerUuid());
        if (seller.isOnline()) {
            MessageUtil.sendMessage(plugin, seller.getPlayer(), MessageKeys.BID_ENDED_NO_WINNER, "{item}", FormatUtil.titleItemFormat(bid.getItemStack()));
        }
    }

    /**
     * @param endDate the date the bid was set to end at (fixed at creation time, unlike a classic
     *                auction which recomputes its deadline from the global expiration delay).
     */
    static boolean isExpired(Date endDate, Date now) {
        return endDate.getTime() <= now.getTime();
    }
}
