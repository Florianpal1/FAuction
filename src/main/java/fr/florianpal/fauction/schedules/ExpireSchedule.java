
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
import fr.florianpal.fauction.objects.Bill;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.bukkit.Bukkit.getServer;

public class ExpireSchedule implements Runnable {

    private final FAuction plugin;
    private List<Auction> auctions = new ArrayList<>();
    private List<Bill> bills = new ArrayList<>();
    public ExpireSchedule(FAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        TaskChain<ArrayList<Auction>> chainAuction = plugin.getAuctionCommandManager().getAuctions();
        chainAuction.sync(() -> {
            this.auctions = new ArrayList<>();
            this.auctions = chainAuction.getTaskData("auctions");
            for (Auction auction : this.auctions) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(auction.getDate());
                cal.add(Calendar.SECOND, plugin.getConfigurationManager().getGlobalConfig().getAuctionTime());
                if (cal.getTime().getTime() <= Calendar.getInstance().getTime().getTime()) {
                    plugin.getExpireCommandManager().addExpire(auction);
                    plugin.getAuctionCommandManager().deleteAuction(auction.getId());
                }
            }
        }).execute();

        TaskChain<ArrayList<Bill>> chainBill = plugin.getBillCommandManager().getBills();
        chainBill.sync(() -> {

            this.bills = new ArrayList<>();
            this.bills = chainBill.getTaskData("bills");
            for (Bill bill : this.bills) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(bill.getDate());
                cal.add(Calendar.SECOND, plugin.getConfigurationManager().getGlobalConfig().getBillTime());
                if (cal.getTime().getTime() <= Calendar.getInstance().getTime().getTime() && bill.getPlayerBidderUuid() != null) {

                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(bill.getPlayerBidderUuid());

                    if (offlinePlayer.isOnline()) {
                        Player player = offlinePlayer.getPlayer();

                        plugin.getVaultIntegrationManager().getEconomy().withdrawPlayer(player, bill.getPrice());
                        EconomyResponse economyResponse4 = plugin.getVaultIntegrationManager().getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bill.getPlayerUuid()), bill.getPrice());
                        if (!economyResponse4.transactionSuccess()) {
                            return;
                        }

                        if (player.getInventory().firstEmpty() == -1) {
                            player.getWorld().dropItem(player.getLocation(), bill.getItemStack());
                        } else {
                            player.getInventory().addItem(bill.getItemStack());
                        }

                        if (plugin.getConfigurationManager().getGlobalConfig().isBillOnBuyCommandUse()) {
                            String command = plugin.getConfigurationManager().getGlobalConfig().getBillOnBuyCommand();
                            command = command.replace("{OwnerName}", bill.getPlayerName());
                            command = command.replace("{Amount}", String.valueOf(bill.getItemStack().getAmount()));
                            if (!bill.getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                                command = command.replace("{ItemName}", bill.getItemStack().getItemMeta().getDisplayName());
                            } else {
                                command = command.replace("{ItemName}", bill.getItemStack().getType().name().replace('_', ' ').toLowerCase());
                            }
                            command = command.replace("{BuyerName}", player.getName());
                            command = command.replace("{ItemPrice}", String.valueOf(bill.getPrice()));
                            getServer().dispatchCommand(getServer().getConsoleSender(), command);
                        }
                    }
                } else if (cal.getTime().getTime() <= Calendar.getInstance().getTime().getTime()) {
                    plugin.getExpireCommandManager().addExpire(bill);
                    plugin.getAuctionCommandManager().deleteAuction(bill.getId());
                }
            }
        }).execute();

    }
}
