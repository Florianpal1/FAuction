package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.queries.ExpireQueries;
import fr.florianpal.fauction.utils.SerializationUtil;

import java.util.*;
import java.util.stream.Collectors;

public class ExpireCommandManager {

    private final ExpireQueries expireQueries;

    private Map<UUID, List<Auction>> cache = new HashMap<>();

    private List<Auction> sqliteCache = new ArrayList<>();

    private final SQLType sqlType;

    private int idMax = 0;

    public ExpireCommandManager(FAuction plugin) {
        this.expireQueries = plugin.getExpireQueries();
        this.sqliteCache = expireQueries.getExpires();
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

    public void addExpire(Auction auction)  {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(auction);
            idMax = idMax + 1;
        }
        expireQueries.addExpire(auction.getPlayerUUID(), auction.getPlayerName(), SerializationUtil.serialize(auction.getItemStack()), auction.getPrice(), auction.getDate());
    }

    public void deleteExpire(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.removeAll(sqliteCache.stream().filter(a -> a.getId() == id).collect(Collectors.toList()));
        }
        expireQueries.deleteExpire(id);
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

    public Map<UUID, List<Auction>> getCache() {
        return cache;
    }
}