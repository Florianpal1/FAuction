package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.queries.AuctionQueries;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;


public class AuctionCommandManager {

    private final AuctionQueries auctionQueries;

    private Map<UUID, List<Auction>> cache = new HashMap<>();

    private List<Auction> sqliteCache = new ArrayList<>();

    private final SQLType sqlType;

    private int idMax = 0;

    public AuctionCommandManager(FAuction plugin) {
        this.auctionQueries = plugin.getAuctionQueries();
        this.sqliteCache = auctionQueries.getAuctions();
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

    public void addAuction(Player player, ItemStack item, double price)  {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(new Auction(idMax, player.getUniqueId(), player.getName(), price, SerializationUtil.serialize(item), Calendar.getInstance().getTime().getTime()));
            idMax = idMax + 1;
        }
        auctionQueries.addAuction(player.getUniqueId(), player.getName(), SerializationUtil.serialize(item), price, Calendar.getInstance().getTime());
    }

    public void saveAllAuctionInBddFromSQLiteCache()  {
        for (Auction auction : sqliteCache) {
            auctionQueries.addAuction(auction.getPlayerUUID(), auction.getPlayerName(), SerializationUtil.serialize(auction.getItemStack()), auction.getPrice(), auction.getDate());
        }
    }

    public void deleteAuction(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.removeAll(sqliteCache.stream().filter(a -> a.getId() == id).collect(Collectors.toList()));
        }
        auctionQueries.deleteAuctions(id);
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

    public Map<UUID, List<Auction>> getCache() {
        return cache;
    }
}