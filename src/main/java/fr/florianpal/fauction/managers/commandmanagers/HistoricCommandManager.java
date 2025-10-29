package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Historic;
import fr.florianpal.fauction.queries.HistoricQueries;
import fr.florianpal.fauction.utils.SerializationUtil;

import java.util.*;
import java.util.stream.Collectors;

public class HistoricCommandManager {
    private final HistoricQueries historicQueries;

    private Map<UUID, List<Historic>> cache = new HashMap<>();

    private List<Historic> sqliteCache = new ArrayList<>();

    private final SQLType sqlType;

    private int idMax = 0;

    public HistoricCommandManager(FAuction plugin) {
        this.historicQueries = plugin.getHistoricQueries();
        this.sqliteCache = historicQueries.getHistorics();
        this.sqlType = plugin.getConfigurationManager().getDatabase().getSqlType();
        if (!sqliteCache.isEmpty()) {
            this.idMax = sqliteCache.stream().max(Comparator.comparing(Historic::getId)).get().getId() + 1;
        }
        updateCache();
    }

    public List<Historic> getHistorics(UUID uuid) {
        if (SQLType.SQLite.equals(sqlType)) {
            return sqliteCache.stream().filter(a -> a.getPlayerUUID().equals(uuid)).collect(Collectors.toList());
        }
        return historicQueries.getHistorics(uuid);
    }

    public void addHistoric(Auction auction, UUID playerBuyerUUID, String playerBuyerName)  {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.add(new Historic(idMax, auction.getPlayerUUID(), auction.getPlayerName(), playerBuyerUUID, playerBuyerName, auction.getPrice(), SerializationUtil.serialize(auction.getItemStack()), auction.getDate().getTime(), new Date().getTime()));
            idMax = idMax + 1;
        }
        historicQueries.addHistoric(auction, playerBuyerUUID, playerBuyerName);
    }

    public void deleteAll() {
        if (SQLType.SQLite.equals(sqlType)) {
            sqliteCache.clear();
        }
        historicQueries.deleteAll();
    }

    public void updateCache() {
        List<Historic> historics = historicQueries.getHistorics();

        Map<UUID, List<Historic>> tempCache = new HashMap<>();
        for (Historic historic : historics) {
            if (!tempCache.containsKey(historic.getPlayerUUID())) {
                tempCache.put(historic.getPlayerUUID(), new ArrayList<>());
            }
            tempCache.get(historic.getPlayerUUID()).add(historic);
        }
        this.cache = tempCache;
    }

    public Map<UUID, List<Historic>> getCache() {
        return cache;
    }
}