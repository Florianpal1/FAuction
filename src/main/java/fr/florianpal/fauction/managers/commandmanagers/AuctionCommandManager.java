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

    private final FAuction plugin;

    private final AuctionQueries auctionQueries;

    @Getter
    private Map<UUID, List<Auction>> cache = new HashMap<>();

    private final List<Auction> sqliteCache;

    private final SQLType sqlType;

    private int idMax = 0;

    public AuctionCommandManager(FAuction plugin) {
        this.plugin = plugin;
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
        byte[] serializedItem = SerializationUtil.serialize(item);
        Date date = Calendar.getInstance().getTime();

        // The database row is what has to survive a crash ; the cache (and its id counter, which
        // must stay in lockstep with SQLite's own AUTOINCREMENT sequence) is only advanced once the
        // write is confirmed, otherwise a failed insert would drift the two id spaces apart and a
        // later row could reuse an id still referenced by a stale cache entry.
        if (!auctionQueries.addAuction(player.getUniqueId(), player.getName(), serializedItem, price, date)) {
            return false;
        }

        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(new Auction(idMax, player.getUniqueId(), player.getName(), price, serializedItem, date.getTime()));
            idMax = idMax + 1;
        }
        return true;
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
     * @return true if this call is the one that removed the auction, from both the cache and the
     * database. The database is checked first and is the one allowed to refuse the removal ; a row
     * that fails to delete (transient database error, or a row that was never actually persisted by a
     * failed restore()) is kept on the market from the cache instead of being evicted with nothing to
     * show for it.
     */
    public synchronized boolean deleteAuction(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            boolean removedInDb = auctionQueries.deleteAuctions(id);
            if (!removedInDb) {
                plugin.getLogger().severe("Auction " + id + " could not be deleted from the database ; kept on the market instead of being lost from the cache for nothing.");
                return false;
            }
            // The cache is the authority in SQLite mode, the rows being rewritten on shutdown.
            return removeFromCache(id);
        }
        return auctionQueries.deleteAuctions(id);
    }

    /**
     * Puts back an auction removed by a claim, when the transaction could not be completed.
     */
    public synchronized void restore(Auction auction) {
        byte[] serializedItem = SerializationUtil.serialize(auction.getItemStack());
        boolean savedInDb = auctionQueries.addAuction(auction.getPlayerUUID(), auction.getPlayerName(), serializedItem, auction.getPrice(), auction.getDate());

        if (!savedInDb) {
            // The row is already gone from the market at this point (deleteAuction succeeded when it
            // was claimed). In SQLite mode it stays visible from the cache rather than losing the item
            // outright, at the cost of a database desync a non-clean restart could still turn into
            // data loss ; in a shared database mode there is no cache to fall back on, so the item is
            // simply lost.
            String outcome = SQLType.SQLite.equals(sqlType)
                    ? "it stays visible from the cache only until the next successful save"
                    : "the item is lost, there is no cache to fall back on outside of SQLite mode";
            plugin.getLogger().severe("Restoring auction of " + auction.getPlayerName() + " to the database failed ; " + outcome + ".");
        }

        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(new Auction(idMax, auction.getPlayerUUID(), auction.getPlayerName(), auction.getPrice(), serializedItem, auction.getDate().getTime()));
            idMax = idMax + 1;
        }
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