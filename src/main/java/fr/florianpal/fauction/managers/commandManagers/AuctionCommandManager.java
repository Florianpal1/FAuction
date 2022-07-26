
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
import fr.florianpal.fauction.queries.AuctionQueries;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;


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

    public TaskChain<Auction> auctionExist(int id) {
        return auctionQueries.getAuction(id);
    }
}