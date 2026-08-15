package fr.florianpal.fauction.schedules;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.CacheType;
import fr.florianpal.fauction.events.CacheBidReloadEvent;
import fr.florianpal.fauction.events.CacheHistoricReloadEvent;
import fr.florianpal.fauction.events.CacheReloadEvent;
import fr.florianpal.fauction.managers.commandmanagers.AuctionCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.BidCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.ExpireCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.HistoricCommandManager;
import org.bukkit.Bukkit;

public class CacheSchedule implements Runnable {

    private final ExpireCommandManager expireCommandManager;

    private final HistoricCommandManager historicCommandManager;

    private final AuctionCommandManager auctionCommandManager;

    private final BidCommandManager bidCommandManager;

    public CacheSchedule(FAuction plugin) {
        this.auctionCommandManager = plugin.getAuctionCommandManager();
        this.historicCommandManager = plugin.getHistoricCommandManager();
        this.expireCommandManager = plugin.getExpireCommandManager();
        this.bidCommandManager = plugin.getBidCommandManager();
    }

    @Override
    public void run() {
        FAuction.newChain().async(() -> {
            expireCommandManager.updateCache();
            historicCommandManager.updateCache();
            auctionCommandManager.updateCache();
            bidCommandManager.updateCache();
        }).sync(() -> {
            Bukkit.getPluginManager().callEvent(new CacheReloadEvent(expireCommandManager.getCache(), CacheType.EXPIRE));
            Bukkit.getPluginManager().callEvent(new CacheReloadEvent(auctionCommandManager.getCache(), CacheType.AUCTION));
            Bukkit.getPluginManager().callEvent(new CacheHistoricReloadEvent(historicCommandManager.getCache()));
            Bukkit.getPluginManager().callEvent(new CacheBidReloadEvent(bidCommandManager.getCache()));
        }).execute();
    }
}
