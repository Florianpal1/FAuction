
/*
 * Copyright (C) 2022 Florianpal
 *
 * This program is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses/>.
 *
 * Last modification : 07/01/2022 23:07
 *
 *  @author Florianpal.
 */

package fr.florianpal.fauction.managers.commandManagers;

import co.aikar.taskchain.TaskChain;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.queries.ExpireQueries;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.ExecutionException;


public class ExpireCommandManager {
    private final ExpireQueries expireQueries;

    public ExpireCommandManager(FAuction plugin) {
        this.expireQueries = plugin.getExpireQueries();
    }

    public TaskChain<ArrayList<Auction>> getAuctions() {
        return expireQueries.getAuctions();
    }

    public TaskChain<ArrayList<Auction>> getAuctions(UUID uuid) {
        return expireQueries.getAuctions(uuid);
    }

    public void addAuction(Player player, ItemStack item, double price)  {
        expireQueries.addAuction(player.getUniqueId(), player.getName(),item.serializeAsBytes(), price, Calendar.getInstance().getTime());
    }

    public void addAuction(Auction auction)  {
        expireQueries.addAuction(auction.getPlayerUuid(), auction.getPlayerName(), auction.getItemStack().serializeAsBytes(), auction.getPrice(), auction.getDate());
    }

    public void deleteAuction(int id) {
        expireQueries.deleteAuctions(id);
    }

    public TaskChain<Auction> auctionExist(int id) {
        return expireQueries.getAuction(id);
    }
}