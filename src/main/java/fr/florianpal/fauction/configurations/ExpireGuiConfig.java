
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

import fr.florianpal.fauction.objects.Barrier;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;

public class ExpireGuiConfig {
    private List<Barrier> barrierBlocks = new ArrayList<>();
    private List<Barrier> previousBlocks = new ArrayList<>();
    private List<Barrier> nextBlocks = new ArrayList<>();
    private List<Integer> expireBlocks = new ArrayList<>();
    private List<Barrier> auctionGuiBlocks = new ArrayList<>();
    private int size = 27;
    private String title = "";
    private List<String> description = new ArrayList<>();
    private String nameGui = "";


    public void load(Configuration config) {
        barrierBlocks = new ArrayList<>();
        previousBlocks = new ArrayList<>();
        nextBlocks = new ArrayList<>();
        expireBlocks = new ArrayList<>();
        auctionGuiBlocks = new ArrayList<>();
        description = new ArrayList<>();

        for (String index : config.getConfigurationSection("block").getKeys(false)) {
            if (config.getString("block." + index + ".utility").equalsIgnoreCase("previous")) {

                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        new Barrier(
                                Integer.parseInt(index),
                                Material.getMaterial(config.getString("block." + index + ".replacement.material")),
                                config.getString("block." + index + ".replacement.title"),
                                config.getStringList("block." + index + ".replacement.description")
                        )
                );
                previousBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("next")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        new Barrier(
                                Integer.parseInt(index),
                                Material.getMaterial(config.getString("block." + index + ".replacement.material")),
                                config.getString("block." + index + ".replacement.title"),
                                config.getStringList("block." + index + ".replacement.description")
                        )

                );
                nextBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("barrier")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                barrierBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("expire")) {
                expireBlocks.add(Integer.valueOf(index));
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("auctionGui")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                auctionGuiBlocks.add(barrier);
         }
        }
        size = config.getInt("gui.size");
        nameGui = config.getString("gui.name");
        title = config.getString("gui.title");
        description.addAll(config.getStringList("gui.description"));

    }

    public String getTitle() {
        return title;
    }

    public List<String> getDescription() {
        return description;
    }

    public String getNameGui() {
        return nameGui;
    }

    public List<Barrier> getBarrierBlocks() {
        return barrierBlocks;
    }

    public List<Barrier> getPreviousBlocks() {
        return previousBlocks;
    }

    public List<Barrier> getNextBlocks() {
        return nextBlocks;
    }

    public int getSize() {
        return size;
    }

    public List<Integer> getExpireBlocks() {
        return expireBlocks;
    }

    public List<Barrier> getAuctionGuiBlocks() {
        return auctionGuiBlocks;
    }
}
