package fr.florianpal.fauction.schedules;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.events.ExpireAddEvent;
import fr.florianpal.fauction.objects.Auction;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ExpireSchedule implements Runnable {

    private final FAuction plugin;

    private List<Auction> auctions = new ArrayList<>();

    public ExpireSchedule(FAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        FAuction.newChain().asyncFirst(() -> plugin.getAuctionCommandManager().getAuctions()).syncLast(auctionList -> {
            this.auctions = auctionList;
            int delay = plugin.getConfigurationManager().getGlobalConfig().getTime();
            Date now = Calendar.getInstance().getTime();

            for (Auction auction : this.auctions) {
                if (isExpired(auction.getDate(), delay, now)) {
                    // Reserve first : an auction bought in the meantime must not be given back to
                    // its owner on top of having been delivered to the buyer.
                    if (!plugin.getAuctionCommandManager().deleteAuction(auction.getId())) {
                        continue;
                    }
                    plugin.getExpireCommandManager().addExpire(auction);
                    Bukkit.getPluginManager().callEvent(new ExpireAddEvent(auction.getPlayerUUID(), auction));
                }
            }
        }).execute();
    }

    /**
     * @param creation date the auction was put on the market.
     * @param delay    lifetime of an auction, in seconds.
     * @return true if the auction has to be moved to the expires.
     */
    static boolean isExpired(Date creation, int delay, Date now) {

        Calendar expiration = Calendar.getInstance();
        expiration.setTime(creation);
        expiration.add(Calendar.SECOND, delay);

        return expiration.getTime().getTime() <= now.getTime();
    }
}
