
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
import fr.florianpal.messagedif.MessageDif;
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

                    if (!offlinePlayer.hasPlayedBefore()) {
                        return;
                    }

                    plugin.getVaultIntegrationManager().getEconomy().withdrawPlayer(offlinePlayer, bill.getBet());
                    EconomyResponse economyResponse4 = plugin.getVaultIntegrationManager().getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bill.getPlayerUuid()), bill.getBet());
                    if (!economyResponse4.transactionSuccess()) {
                        return;
                    }

                    plugin.getBillCommandManager().deleteBill(bill.getId());
                    plugin.getExpireCommandManager().addExpire(bill, bill.getPlayerBidderUuid());

                    if (plugin.getConfigurationManager().getGlobalConfig().isBillOnBuyCommandUse()) {
                        String message = plugin.getConfigurationManager().getGlobalConfig().getBillOnBuyMessage();
                        message = message.replace("{OwnerName}", bill.getPlayerName());
                        message = message.replace("{Amount}", String.valueOf(bill.getItemStack().getAmount()));
                        if (!bill.getItemStack().getItemMeta().getDisplayName().equalsIgnoreCase("")) {
                            message = message.replace("{ItemName}", bill.getItemStack().getItemMeta().getDisplayName());
                        } else {
                            message = message.replace("{ItemName}", bill.getItemStack().getType().name().replace('_', ' ').toLowerCase());
                        }
                        message = message.replace("{BuyerName}", bill.getPlayerBidderName());
                        message = message.replace("{ItemPrice}", String.valueOf(bill.getPrice()));
                        MessageDif.newMessage(bill.getPlayerUuid(), message);
                    }
                } else if (cal.getTime().getTime() <= Calendar.getInstance().getTime().getTime()) {
                    plugin.getExpireCommandManager().addExpire(bill);
                    plugin.getBillCommandManager().deleteBill(bill.getId());
                }
            }
        }).execute();

    }
}
