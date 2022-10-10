
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

package fr.florianpal.fauction.configurations;

import fr.florianpal.fauction.objects.AmountBarrier;
import fr.florianpal.fauction.objects.Barrier;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;

public class AmountGuiConfig {
    private List<Barrier> barrierBlocks = new ArrayList<>();

    private  List<AmountBarrier> amountBlocks = new ArrayList<>();
    private List<Barrier> closeBlocks = new ArrayList<>();

    private List<Barrier> confirmBlocks = new ArrayList<>();

    private int size = 27;
    private String nameGui = "";


    public void load(Configuration config) {
        barrierBlocks = new ArrayList<>();
        closeBlocks = new ArrayList<>();
        amountBlocks = new ArrayList<>();
        confirmBlocks = new ArrayList<>();

        for (String index : config.getConfigurationSection("block").getKeys(false)) {
            if (config.getString("block." + index + ".utility").equalsIgnoreCase("barrier")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                barrierBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("close")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                closeBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("amount")) {
                AmountBarrier barrier = new AmountBarrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getInt("block." + index + ".amount")
                );
                amountBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("confirm")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                confirmBlocks.add(barrier);
            }
        }
        size = config.getInt("gui.size");
        nameGui = config.getString("gui.name");

    }

    public String getNameGui() {
        return nameGui;
    }

    public List<Barrier> getBarrierBlocks() {
        return barrierBlocks;
    }

    public List<Barrier> getCloseBlocks() {
        return closeBlocks;
    }

    public int getSize() {
        return size;
    }

    public List<AmountBarrier> getAmountBlocks() {
        return amountBlocks;
    }

    public List<Barrier> getConfirmBlocks() {
        return confirmBlocks;
    }
}
