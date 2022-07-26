
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
import net.milkbowl.vault.permission.Permission;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;

public class LimitationManager {

    private final FAuction plugin;
    private ArrayList<Auction> auctions;
    private boolean canHaveNewAuction = false;
    public LimitationManager(FAuction plugin) {
        this.plugin = plugin;
    }

    public boolean canHaveNewAuction(Player player) throws InterruptedException {
        auctions = new ArrayList<>();
        canHaveNewAuction = false;
        TaskChain<ArrayList<Auction>> chain = plugin.getAuctionCommandManager().getAuctions(player.getUniqueId());
        chain.sync(() -> {
            auctions = chain.getTaskData("auctions");
            if(getAuctionLimitation(player) <= auctions.size()) {
                canHaveNewAuction = true;
            }
        }).execute();
        chain.wait();
        return canHaveNewAuction;
    }

    public int getAuctionLimitation(Player player) {
        Permission perms = plugin.getVaultIntegrationManager().getPerms();
        Map<String, Integer> limitations = plugin.getConfigurationManager().getGlobalConfig().getLimitations();
        String[] playerGroup;
        int limit = limitations.get("default");
        if (perms != null) {
            String primaryGroup = perms.getPrimaryGroup(player);
            if(limitations.containsKey(primaryGroup)) {
                if(limit < limitations.get(primaryGroup)) {
                    limit = limitations.get(primaryGroup);
                }
            }
            playerGroup = perms.getPlayerGroups(player);
            for(String s : playerGroup) {
                if(limitations.containsKey(s)) {
                    if(limit < limitations.get(s)) {
                        limit = limitations.get(s);
                    }
                }
            }
        }
        return limit;
    }
}
