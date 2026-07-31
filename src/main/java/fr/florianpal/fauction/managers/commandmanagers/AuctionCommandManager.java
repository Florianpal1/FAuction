package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.queries.AuctionQueries;
import fr.florianpal.fauction.utils.SerializationUtil;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;


public class AuctionCommandManager {

    private final AuctionQueries auctionQueries;

    @Getter
    private Map<UUID, List<Auction>> cache = new HashMap<>();

    private final List<Auction> sqliteCache;

    private final SQLType sqlType;

    private int idMax = 0;

    public AuctionCommandManager(FAuction plugin) {
        this.auctionQueries = plugin.getAuctionQueries();
        this.sqliteCache = new CopyOnWriteArrayList<>(auctionQueries.getAuctions());
        this.sqlType = plugin.getConfigurationManager().getDatabase().getSqlType();
        if (!sqliteCache.isEmpty()) {
            this.idMax = sqliteCache.stream().max(Comparator.comparing(Auction::getId)).get().getId() + 1;
        }
        updateCache();
    }

    public List<Auction> getAuctions() {
        if (SQLType.SQLite.equals(sqlType)) {
            return sqliteCache;
        }
        return auctionQueries.getAuctions();
    }

    public List<Auction> getAuctions(UUID uuid) {
        if (SQLType.SQLite.equals(sqlType)) {
            return sqliteCache.stream().filter(a -> a.getPlayerUUID().equals(uuid)).collect(Collectors.toList());
        }
        return auctionQueries.getAuctions(uuid);
    }

    /**
     * @return true if the auction has been saved, the item can stay out of the inventory.
     */
    public synchronized boolean addAuction(Player player, ItemStack item, double price)  {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(new Auction(idMax, player.getUniqueId(), player.getName(), price, SerializationUtil.serialize(item), Calendar.getInstance().getTime().getTime()));
            idMax = idMax + 1;
            auctionQueries.addAuction(player.getUniqueId(), player.getName(), SerializationUtil.serialize(item), price, Calendar.getInstance().getTime());
            return true;
        }
        return auctionQueries.addAuction(player.getUniqueId(), player.getName(), SerializationUtil.serialize(item), price, Calendar.getInstance().getTime());
    }

    public void saveAllAuctionInBddFromSQLiteCache()  {
        for (Auction auction : sqliteCache) {
            auctionQueries.addAuction(auction.getPlayerUUID(), auction.getPlayerName(), SerializationUtil.serialize(auction.getItemStack()), auction.getPrice(), auction.getDate());
        }
    }

    /**
     * Reserves an auction and gives it back to the caller. The removal being atomic, exactly one
     * caller gets the auction whatever the number of concurrent attempts, and only that one is
     * allowed to hand the item over.
     *
     * @return the reserved auction, null if someone else took it first.
     */
    public synchronized Auction claim(int id) {

        Auction auction = auctionExist(id);
        if (auction == null) {
            return null;
        }
        return deleteAuction(id) ? auction : null;
    }

    /**
     * @return true if this call is the one that removed the auction.
     */
    public synchronized boolean deleteAuction(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            // The cache is the authority in SQLite mode, the rows being rewritten on shutdown.
            boolean removed = removeFromCache(id);
            auctionQueries.deleteAuctions(id);
            return removed;
        }
        return auctionQueries.deleteAuctions(id);
    }

    /**
     * Puts back an auction removed by a claim, when the transaction could not be completed.
     */
    public synchronized void restore(Auction auction) {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(new Auction(idMax, auction.getPlayerUUID(), auction.getPlayerName(), auction.getPrice(), SerializationUtil.serialize(auction.getItemStack()), auction.getDate().getTime()));
            idMax = idMax + 1;
        }
        auctionQueries.addAuction(auction.getPlayerUUID(), auction.getPlayerName(), SerializationUtil.serialize(auction.getItemStack()), auction.getPrice(), auction.getDate());
    }

    /**
     * Removes a single entry, so two entries sharing an id can never be dropped by the same claim.
     */
    private boolean removeFromCache(int id) {
        for (Auction auction : sqliteCache) {
            if (auction.getId() == id) {
                return sqliteCache.remove(auction);
            }
        }
        return false;
    }

    public void deleteAll() {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.clear();
        }
        auctionQueries.deleteAll();
    }

    public void deleteAllOnlyOnDB() {
        auctionQueries.deleteAll();
    }

    public Auction auctionExist(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            Optional<Auction> auction = sqliteCache.stream().filter(a -> a.getId() == id).findFirst();
            return auction.isPresent() ? auction.get() : null;
        }
        return auctionQueries.getAuction(id);
    }

    public void updateCache() {
        List<Auction> auctions = auctionQueries.getAuctions();

        Map<UUID, List<Auction>> tempCache = new HashMap<>();
        for (Auction auction : auctions) {
            if (!tempCache.containsKey(auction.getPlayerUUID())) {
                tempCache.put(auction.getPlayerUUID(), new ArrayList<>());
            }
            tempCache.get(auction.getPlayerUUID()).add(auction);
        }
        this.cache = tempCache;
    }
}