
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
import fr.florianpal.fauction.objects.Bill;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class LimitationManager {

    private final FAuction plugin;
    public LimitationManager(FAuction plugin) {
        this.plugin = plugin;
    }

    public boolean canHaveNewAuction(Player player) {
        ArrayList[] auctions = new ArrayList[]{new ArrayList<>()};
        AtomicBoolean canHaveNewAuction = new AtomicBoolean(false);
        TaskChain<ArrayList<Auction>> chain = plugin.getAuctionCommandManager().getAuctions(player.getUniqueId());
        chain.sync(() -> {
            auctions[0] = chain.getTaskData("auctions");
            if(getAuctionLimitation(player) <= auctions[0].size()) {
                canHaveNewAuction.set(true);
            }
        }).execute();
        return canHaveNewAuction.get();
    }

    public int getAuctionLimitation(Player player) {
        Permission perms = plugin.getVaultIntegrationManager().getPerms();
        Map<String, Integer> limitations = plugin.getConfigurationManager().getGlobalConfig().getAuctionLimitations();
        String[] playerGroup;
        int limit = limitations.get("default");
        if (perms != null) {
            String primaryGroup = perms.getPrimaryGroup(player);
            if(limitations.containsKey(primaryGroup) && limit < limitations.get(primaryGroup)) {
                limit = limitations.get(primaryGroup);
            }
            playerGroup = perms.getPlayerGroups(player);
            for(String s : playerGroup) {
                if(limitations.containsKey(s) && limit < limitations.get(s)) {
                    limit = limitations.get(s);
                }
            }
        }
        return limit;
    }

    public boolean canHaveNewBill(Player player) {
        ArrayList[] auctions = new ArrayList[]{new ArrayList<>()};
        AtomicBoolean canHaveNewBill = new AtomicBoolean(false);
        TaskChain<ArrayList<Bill>> chain = plugin.getBillCommandManager().getBills(player.getUniqueId());
        chain.sync(() -> {
            auctions[0] = chain.getTaskData("bills");
            if(getBillLimitation(player) <= auctions[0].size()) {
                canHaveNewBill.set(true);
            }
        }).execute();
        return canHaveNewBill.get();
    }

    public int getBillLimitation(Player player) {
        Permission perms = plugin.getVaultIntegrationManager().getPerms();
        Map<String, Integer> limitations = plugin.getConfigurationManager().getGlobalConfig().getBillLimitations();
        String[] playerGroup;
        int limit = limitations.get("default");
        if (perms != null) {
            String primaryGroup = perms.getPrimaryGroup(player);
            if(limitations.containsKey(primaryGroup) && limit < limitations.get(primaryGroup)) {
                limit = limitations.get(primaryGroup);
            }
            playerGroup = perms.getPlayerGroups(player);
            for(String s : playerGroup) {
                if(limitations.containsKey(s) && limit < limitations.get(s)) {
                    limit = limitations.get(s);
                }
            }
        }
        return limit;
    }
}
