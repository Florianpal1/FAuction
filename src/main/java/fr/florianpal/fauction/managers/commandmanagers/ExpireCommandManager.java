package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.queries.ExpireQueries;
import fr.florianpal.fauction.utils.SerializationUtil;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ExpireCommandManager {

    private final FAuction plugin;

    private final ExpireQueries expireQueries;

    @Getter
    private Map<UUID, List<Auction>> cache = new HashMap<>();

    private final List<Auction> sqliteCache;

    private final SQLType sqlType;

    private int idMax = 0;

    public ExpireCommandManager(FAuction plugin) {
        this.plugin = plugin;
        this.expireQueries = plugin.getExpireQueries();
        this.sqliteCache = new CopyOnWriteArrayList<>(expireQueries.getExpires());
        this.sqlType = plugin.getConfigurationManager().getDatabase().getSqlType();
        if (!sqliteCache.isEmpty()) {
            this.idMax = sqliteCache.stream().max(Comparator.comparing(Auction::getId)).get().getId() + 1;
        }
        updateCache();
    }

    public List<Auction> getExpires() {
        if (SQLType.SQLite.equals(sqlType)) {
            return sqliteCache;
        }
        return expireQueries.getExpires();
    }

    public List<Auction> getExpires(UUID uuid) {
        if (SQLType.SQLite.equals(sqlType)) {
            return sqliteCache.stream().filter(a -> a.getPlayerUUID().equals(uuid)).collect(Collectors.toList());
        }
        return expireQueries.getExpires(uuid);
    }

    /**
     * @return true if the expired auction has been saved, the item can be considered moved off the
     * market. Only advances the cache (and its id counter, kept in lockstep with SQLite's own
     * AUTOINCREMENT sequence) once the database write is confirmed, so a failed insert can never
     * leave an item that only exists in the cache.
     */
    public synchronized boolean addExpire(Auction auction)  {
        byte[] serializedItem = SerializationUtil.serialize(auction.getItemStack());

        if (!expireQueries.addExpire(auction.getPlayerUUID(), auction.getPlayerName(), serializedItem, auction.getPrice(), auction.getDate())) {
            return false;
        }

        if (SQLType.SQLite.equals(sqlType)) {
            // Own id, otherwise an expire could share the id of another entry of the cache and a
            // single claim would drop them both.
            sqliteCache.add(new Auction(idMax, auction.getPlayerUUID(), auction.getPlayerName(), auction.getPrice(), serializedItem, auction.getDate().getTime()));
            idMax = idMax + 1;
        }
        return true;
    }

    /**
     * Reserves an expired auction and gives it back to the caller.
     *
     * @return the reserved expire, null if someone else took it first.
     */
    public synchronized Auction claim(int id) {

        Auction expire = expireExist(id);
        if (expire == null) {
            return null;
        }
        return deleteExpire(id) ? expire : null;
    }

    /**
     * @return true if this call is the one that removed the expired auction, from both the cache and
     * the database. The database is checked first, so a row that fails to delete is kept visible from
     * the cache instead of being evicted with nothing to show for it.
     */
    public synchronized boolean deleteExpire(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            boolean removedInDb = expireQueries.deleteExpire(id);
            if (!removedInDb) {
                plugin.getLogger().severe("Expired item " + id + " could not be deleted from the database ; kept visible instead of being lost from the cache for nothing.");
                return false;
            }
            // The cache is the authority in SQLite mode, the rows being rewritten on shutdown.
            return removeFromCache(id);
        }
        return expireQueries.deleteExpire(id);
    }

    /**
     * Removes a single entry, so two entries sharing an id can never be dropped by the same claim.
     */
    private boolean removeFromCache(int id) {
        for (Auction expire : sqliteCache) {
            if (expire.getId() == id) {
                return sqliteCache.remove(expire);
            }
        }
        return false;
    }

    public void deleteAll() {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.clear();
        }
        expireQueries.deleteAll();
    }

    public Auction expireExist(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            Optional<Auction> auction = sqliteCache.stream().filter(a -> a.getId() == id).findFirst();
            return auction.isPresent() ? auction.get() : null;
        }
        return expireQueries.getExpire(id);
    }

    public void updateCache() {
        List<Auction> expires = expireQueries.getExpires();

        Map<UUID, List<Auction>> tempCache = new HashMap<>();
        for (Auction expire : expires) {
            if (!tempCache.containsKey(expire.getPlayerUUID())) {
                tempCache.put(expire.getPlayerUUID(), new ArrayList<>());
            }
            tempCache.get(expire.getPlayerUUID()).add(expire);
        }
        this.cache = tempCache;
    }
}