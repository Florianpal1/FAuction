
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


public class ExpireCommandManager {
    private final ExpireQueries expireQueries;

    public ExpireCommandManager(FAuction plugin) {
        this.expireQueries = plugin.getExpireQueries();
    }

    public TaskChain<ArrayList<Auction>> getExpires() {
        return expireQueries.getExpires();
    }

    public TaskChain<ArrayList<Auction>> getExpires(UUID uuid) {
        return expireQueries.getExpires(uuid);
    }

    public void addExpire(Player player, ItemStack item, double price)  {
        expireQueries.addAuction(player.getUniqueId(), player.getName(),item.serializeAsBytes(), price, Calendar.getInstance().getTime());
    }

    public void addExpire(Auction auction)  {
        expireQueries.addAuction(auction.getPlayerUuid(), auction.getPlayerName(), auction.getItemStack().serializeAsBytes(), auction.getPrice(), auction.getDate());
    }

    public void addExpire(Auction auction, UUID newOwner)  {
        expireQueries.addAuction(newOwner, auction.getPlayerName(), auction.getItemStack().serializeAsBytes(), auction.getPrice(), auction.getDate());
    }

    public void deleteExpire(int id) {
        expireQueries.deleteAuctions(id);
    }

    public TaskChain<Auction> expireExist(int id) {
        return expireQueries.getExpire(id);
    }
}