
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
import fr.florianpal.fauction.objects.Bill;
import fr.florianpal.fauction.queries.BillQueries;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;


public class BillCommandManager {
    private final BillQueries billQueries;

    public BillCommandManager(FAuction plugin) {
        this.billQueries = plugin.getBillQueries();
    }

    public TaskChain<ArrayList<Bill>> getBills() {
        return billQueries.getBills();
    }

    public TaskChain<ArrayList<Bill>> getBills(UUID uuid) {
        return billQueries.getBills(uuid);
    }

    public void addBill(Player player, ItemStack item, double price)  {
        billQueries.addBill(player.getUniqueId(), player.getName(),item.serializeAsBytes(), price, Calendar.getInstance().getTime());
    }

    public void makeOffer(int id, Player player, double newBet) {
        billQueries.updateBidder(id, player.getUniqueId(), player.getName(), newBet);
    }


    public void deleteBill(int id) {
        billQueries.deleteBill(id);
    }

    public TaskChain<Bill> billExist(int id) {
        return billQueries.getBill(id);
    }
}