
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

public class MainGuiConfig {
    private List<Barrier> barrierBlocks = new ArrayList<>();
    private List<Barrier> previousBlocks = new ArrayList<>();
    private List<Barrier> nextBlocks = new ArrayList<>();
    private List<Barrier> expireBlocks = new ArrayList<>();
    private  List<Integer> itemBlocks = new ArrayList<>();
    private List<Barrier> closeBlocks = new ArrayList<>();
    private List<Barrier> playerBlocks = new ArrayList<>();

    private List<Barrier> billBlocks = new ArrayList<>();
    private int size = 27;
    private String nameGui = "";
    private String titleAuction = "";
    private List<String> descriptionAuction = new ArrayList<>();
    private String titleBill = "";
    private List<String> descriptionBill = new ArrayList<>();


    public void load(Configuration config) {
        barrierBlocks = new ArrayList<>();
        previousBlocks = new ArrayList<>();
        nextBlocks = new ArrayList<>();
        expireBlocks = new ArrayList<>();
        itemBlocks = new ArrayList<>();
        closeBlocks = new ArrayList<>();
        billBlocks = new ArrayList<>();
        playerBlocks = new ArrayList<>();
        descriptionAuction = new ArrayList<>();
        descriptionBill = new ArrayList<>();

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
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("expire")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                expireBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("player")) {
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
                playerBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("barrier")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                barrierBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("item")) {
                itemBlocks.add(Integer.valueOf(index));
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("close")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                closeBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("bill")) {
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
                billBlocks.add(barrier);
            }
        }
        size = config.getInt("gui.size");
        nameGui = config.getString("gui.name");
        titleAuction = config.getString("auction.title");
        descriptionAuction.addAll(config.getStringList("auction.description"));
        titleBill = config.getString("bill.title");
        descriptionBill.addAll(config.getStringList("bill.description"));

    }

    public String getTitleAuction() {
        return titleAuction;
    }

    public List<String> getDescriptionAuction() {
        return descriptionAuction;
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

    public List<Barrier> getExpireBlocks() {
        return expireBlocks;
    }

    public List<Barrier> getNextBlocks() {
        return nextBlocks;
    }

    public List<Integer> getItemBlocks() {
        return itemBlocks;
    }

    public List<Barrier> getCloseBlocks() {
        return closeBlocks;
    }

    public int getSize() {
        return size;
    }

    public List<Barrier> getPlayerBlocks() {
        return playerBlocks;
    }

    public List<Barrier> getBillBlocks() {
        return billBlocks;
    }

    public String getTitleBill() {
        return titleBill;
    }

    public List<String> getDescriptionBill() {
        return descriptionBill;
    }
}
