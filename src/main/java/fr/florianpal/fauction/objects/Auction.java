
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

package fr.florianpal.fauction.objects;

import org.bukkit.inventory.ItemStack;

import java.util.Date;
import java.util.UUID;

public class Auction {
    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private final double price;
    private final ItemStack itemStack;
    private final Date date;

    public Auction(int id, UUID playerUuid, String playerName, double price, byte[] item, long date) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.price = price;
        this.itemStack = ItemStack.deserializeBytes(item);
        this.date = new Date(date);
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public double getPrice() {
        return price;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Date getDate() {
        return date;
    }
}
