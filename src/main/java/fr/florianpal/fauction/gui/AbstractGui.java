
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

package fr.florianpal.fauction.gui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.managers.commandManagers.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractGui implements InventoryHolder, Listener {
    protected Inventory inv;
    protected final FAuction plugin;
    protected Player player;
    protected int page;
    protected final GlobalConfig globalConfig;
    protected final CommandManager commandManager;

    protected AbstractGui(FAuction plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = page;
        this.commandManager = plugin.getCommandManager();
        inv = null;
        this.globalConfig = plugin.getConfigurationManager().getGlobalConfig();

        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugins()[0]);
    }

    protected void initGui(String title, int size) {
        inv = Bukkit.createInventory(this, size, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    protected void openInventory(Player p) {
        p.openInventory(this.inv);
    }
}
