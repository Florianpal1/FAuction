package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Bid;
import fr.florianpal.fauction.objects.BidHistoric;
import fr.florianpal.fauction.queries.BidHistoricQueries;
import fr.florianpal.fauction.utils.SerializationUtil;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

public class BidHistoricCommandManager {

    private final BidHistoricQueries bidHistoricQueries;

    @Getter
    private Map<UUID, List<BidHistoric>> cache = new HashMap<>();

    private List<BidHistoric> sqliteCache = new ArrayList<>();

    private final SQLType sqlType;

    private int idMax = 0;

    public BidHistoricCommandManager(FAuction plugin) {
        this.bidHistoricQueries = plugin.getBidHistoricQueries();
        this.sqliteCache = bidHistoricQueries.getBidHistorics();
        this.sqlType = plugin.getConfigurationManager().getDatabase().getSqlType();
        if (!sqliteCache.isEmpty()) {
            this.idMax = sqliteCache.stream().max(Comparator.comparing(BidHistoric::getId)).get().getId() + 1;
        }
        updateCache();
    }

    public List<BidHistoric> getBidHistorics(UUID sellerUuid) {
        if (SQLType.SQLite.equals(sqlType)) {
            return sqliteCache.stream().filter(b -> b.getSellerUuid().equals(sellerUuid)).collect(Collectors.toList());
        }
        return bidHistoricQueries.getBidHistorics(sellerUuid);
    }

    public void addBidHistoric(Bid bid, UUID buyerUuid, String buyerName) {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(new BidHistoric(idMax, bid.getSellerUuid(), bid.getSellerName(), buyerUuid, buyerName,
                    SerializationUtil.serialize(bid.getItemStack()), bid.getStartPrice(), bid.getCurrentPrice(),
                    bid.getStartDate().getTime(), new Date().getTime()));
            idMax = idMax + 1;
        }
        bidHistoricQueries.addBidHistoric(bid, buyerUuid, buyerName);
    }

    public void deleteAll() {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.clear();
        }
        bidHistoricQueries.deleteAll();
    }

    public void updateCache() {
        List<BidHistoric> bidHistorics = bidHistoricQueries.getBidHistorics();

        Map<UUID, List<BidHistoric>> tempCache = new HashMap<>();
        for (BidHistoric bidHistoric : bidHistorics) {
            if (!tempCache.containsKey(bidHistoric.getSellerUuid())) {
                tempCache.put(bidHistoric.getSellerUuid(), new ArrayList<>());
            }
            tempCache.get(bidHistoric.getSellerUuid()).add(bidHistoric);
        }
        this.cache = tempCache;
    }
}
