package fr.florianpal.fauction.managers.commandManagers;

import co.aikar.taskchain.TaskChain;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.queries.AuctionQueries;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.ExecutionException;


public class AuctionCommandManager {
    private final AuctionQueries auctionQueries;

    public AuctionCommandManager(FAuction plugin) {
        this.auctionQueries = plugin.getAuctionQueries();
    }

    public TaskChain<ArrayList<Auction>> getAuctions() {
        return auctionQueries.getAuctions();
    }

    public TaskChain<ArrayList<Auction>> getAuctions(UUID uuid) {
        return auctionQueries.getAuctions(uuid);
    }

    public void addAuction(Player player, ItemStack item, double price)  {
        auctionQueries.addAuction(player.getUniqueId(), player.getName(),item.serializeAsBytes(), price, Calendar.getInstance().getTime());
    }


    public void deleteAuction(int id) {
        auctionQueries.deleteAuctions(id);
    }

    public boolean auctionExist(int id) throws ExecutionException, InterruptedException {
        return auctionQueries.getAuction(id).get() != null;
    }
}