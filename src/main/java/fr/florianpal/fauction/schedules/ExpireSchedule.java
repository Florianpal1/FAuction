
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

package fr.florianpal.fauction.schedules;

import co.aikar.taskchain.TaskChain;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.objects.Auction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ExpireSchedule implements Runnable {

    private final FAuction plugin;
    private List<Auction> auctions = new ArrayList<>();
    public ExpireSchedule(FAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        TaskChain<ArrayList<Auction>> chain = plugin.getAuctionCommandManager().getAuctions();
        chain.sync(() -> {
            this.auctions = new ArrayList<>();
            this.auctions = chain.getTaskData("auctions");
            for (Auction auction : this.auctions) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(auction.getDate());
                cal.add(Calendar.SECOND, plugin.getConfigurationManager().getGlobalConfig().getTime());
                if (cal.getTime().getTime() <= Calendar.getInstance().getTime().getTime()) {
                    plugin.getExpireCommandManager().addAuction(auction);
                    plugin.getAuctionCommandManager().deleteAuction(auction.getId());
                }
            }
        }).execute();

    }
}
