package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Bid;
import fr.florianpal.fauction.queries.BidQueries;
import fr.florianpal.fauction.utils.SerializationUtil;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class BidCommandManager {

    private final FAuction plugin;

    private final BidQueries bidQueries;

    @Getter
    private Map<UUID, List<Bid>> cache = new HashMap<>();

    private final List<Bid> sqliteCache;

    private final SQLType sqlType;

    // Only used to give a cache-only row (one the database refused, see restore()) an id that can
    // never collide with a real, database-assigned one, which are always positive.
    private int fallbackId = -1;

    public BidCommandManager(FAuction plugin) {
        this.plugin = plugin;
        this.bidQueries = plugin.getBidQueries();
        this.sqliteCache = new CopyOnWriteArrayList<>(bidQueries.getBids());
        this.sqlType = plugin.getConfigurationManager().getDatabase().getSqlType();
        updateCache();
    }

    public List<Bid> getBids() {
        if (SQLType.SQLite.equals(sqlType)) {
            return sqliteCache;
        }
        return bidQueries.getBids();
    }

    public List<Bid> getBids(UUID sellerUuid) {
        if (SQLType.SQLite.equals(sqlType)) {
            return sqliteCache.stream().filter(b -> b.getSellerUuid().equals(sellerUuid)).collect(Collectors.toList());
        }
        return bidQueries.getBids(sellerUuid);
    }

    /**
     * @return true if the bid has been saved, the item can stay out of the seller's inventory.
     */
    public synchronized boolean addBid(Player seller, ItemStack item, double startPrice, Date endDate) {
        byte[] serializedItem = SerializationUtil.serialize(item);
        Date startDate = Calendar.getInstance().getTime();

        // Same rule as AuctionCommandManager#addAuction : the cache is only advanced once the write
        // is confirmed, using the id the database itself assigned.
        int id = bidQueries.addBid(seller.getUniqueId(), seller.getName(), serializedItem, startPrice, startDate, endDate);
        if (id < 0) {
            return false;
        }

        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(new Bid(id, seller.getUniqueId(), seller.getName(), serializedItem, startPrice, startPrice, null, null, startDate.getTime(), endDate.getTime()));
        }
        return true;
    }

    /**
     * Raises a bid from previousPrice to newPrice, rejecting the write if the row was already raised
     * by someone else since the caller last read it (see BidQueries#updateBid).
     *
     * @return true if this call is the one that placed the bid.
     */
    public synchronized boolean placeBid(int id, UUID bidderUuid, String bidderName, double previousPrice, double newPrice) {
        boolean updated = bidQueries.updateBid(id, previousPrice, newPrice, bidderUuid, bidderName);
        if (!updated) {
            return false;
        }

        if (SQLType.SQLite.equals(sqlType)) {
            for (Bid bid : sqliteCache) {
                if (bid.getId() == id) {
                    bid.setCurrentPrice(newPrice);
                    bid.setCurrentBidderUuid(bidderUuid);
                    bid.setCurrentBidderName(bidderName);
                    break;
                }
            }
        }
        return true;
    }

    /**
     * The bidder is now debited before the row is raised (see BidConfirmGui), so a rejected raise is
     * simply refunded on the spot instead of ever needing to unwind a row already showing a bidder
     * who was never actually charged.
     */
    public void saveAllBidInBddFromSQLiteCache() {
        for (Bid bid : sqliteCache) {
            byte[] serializedItem = SerializationUtil.serialize(bid.getItemStack());
            int id = bidQueries.addBid(bid.getSellerUuid(), bid.getSellerName(), serializedItem, bid.getStartPrice(), bid.getStartDate(), bid.getEndDate());

            // addBid always starts the row at startPrice with no bidder ; the current highest bid (and
            // the money already escrowed for it) must be re-applied on top, or a shutdown would erase
            // the bidder without ever refunding them.
            if (id >= 0 && bid.hasBidder()) {
                bidQueries.updateBid(id, bid.getStartPrice(), bid.getCurrentPrice(), bid.getCurrentBidderUuid(), bid.getCurrentBidderName());
            }
        }
    }

    /**
     * Reserves a bid and gives it back to the caller. The removal being atomic, exactly one caller
     * gets the bid whatever the number of concurrent attempts, and only that one is allowed to
     * finalize it (deliver the item, pay the seller).
     *
     * @return the reserved bid, null if someone else took it first.
     */
    public synchronized Bid claim(int id) {

        Bid bid = bidExist(id);
        if (bid == null) {
            return null;
        }
        return deleteBid(id) ? bid : null;
    }

    /**
     * @return true if this call is the one that removed the bid, from both the cache and the
     * database. The database is checked first and is the one allowed to refuse the removal.
     */
    public synchronized boolean deleteBid(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            boolean removedInDb = bidQueries.deleteBid(id);
            if (!removedInDb) {
                plugin.getLogger().severe("Bid " + id + " could not be deleted from the database ; kept on the market instead of being lost from the cache for nothing.");
                return false;
            }
            return removeFromCache(id);
        }
        return bidQueries.deleteBid(id);
    }

    /**
     * Puts back a bid removed by a claim, when the transaction could not be completed. The current
     * bidder/price are preserved so the bid resumes exactly where it left off.
     */
    public synchronized void restore(Bid bid) {
        byte[] serializedItem = SerializationUtil.serialize(bid.getItemStack());
        int id = bidQueries.addBid(bid.getSellerUuid(), bid.getSellerName(), serializedItem, bid.getStartPrice(), bid.getStartDate(), bid.getEndDate());
        boolean savedInDb = id >= 0;

        if (savedInDb && bid.hasBidder()) {
            // addBid always starts the row at startPrice with no bidder ; restore the current highest
            // bid on top of the freshly inserted row so the auction resumes where it left off.
            bidQueries.updateBid(id, bid.getStartPrice(), bid.getCurrentPrice(), bid.getCurrentBidderUuid(), bid.getCurrentBidderName());
        }

        if (!savedInDb) {
            String outcome = SQLType.SQLite.equals(sqlType)
                    ? "it stays visible from the cache only until the next successful save"
                    : "the item is lost, there is no cache to fall back on outside of SQLite mode";
            plugin.getLogger().severe("Restoring bid of " + bid.getSellerName() + " to the database failed ; " + outcome + ".");
        }

        if (SQLType.SQLite.equals(sqlType)) {
            int cacheId = savedInDb ? id : fallbackId--;
            sqliteCache.add(new Bid(cacheId, bid.getSellerUuid(), bid.getSellerName(), serializedItem, bid.getStartPrice(), bid.getCurrentPrice(), bid.getCurrentBidderUuid(), bid.getCurrentBidderName(), bid.getStartDate().getTime(), bid.getEndDate().getTime()));
        }
    }

    /**
     * Removes a single entry, so two entries sharing an id can never be dropped by the same claim.
     */
    private boolean removeFromCache(int id) {
        for (Bid bid : sqliteCache) {
            if (bid.getId() == id) {
                return sqliteCache.remove(bid);
            }
        }
        return false;
    }

    public void deleteAll() {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.clear();
        }
        bidQueries.deleteAll();
    }

    public void deleteAllOnlyOnDB() {
        bidQueries.deleteAll();
    }

    public Bid bidExist(int id) {
        if (SQLType.SQLite.equals(sqlType)) {
            Optional<Bid> bid = sqliteCache.stream().filter(b -> b.getId() == id).findFirst();
            return bid.isPresent() ? bid.get() : null;
        }
        return bidQueries.getBid(id);
    }

    public void updateCache() {
        List<Bid> bids = bidQueries.getBids();

        Map<UUID, List<Bid>> tempCache = new HashMap<>();
        for (Bid bid : bids) {
            if (!tempCache.containsKey(bid.getSellerUuid())) {
                tempCache.put(bid.getSellerUuid(), new ArrayList<>());
            }
            tempCache.get(bid.getSellerUuid()).add(bid);
        }
        this.cache = tempCache;
    }
}
