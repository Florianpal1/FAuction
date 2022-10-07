
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
import fr.florianpal.fauction.objects.Confirm;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfirmGuiConfig {
    private String titleTrue;
    private String titleFalse;
    private List<String> description = new ArrayList<>();
    private String nameGui = "";
    private Integer size = 27;
    private List<Barrier> barrierBlocks = new ArrayList<>();
    private Map<Integer, Confirm> confirmBlocks = new HashMap<>();

    public void load(Configuration config) {
        titleTrue = config.getString("gui.title-true");
        titleFalse = config.getString("gui.title-false");
        nameGui = config.getString("gui.name");
        description = config.getStringList("gui.description");
        size = config.getInt("gui.size");

        barrierBlocks = new ArrayList<>();
        confirmBlocks = new HashMap<>();

        for (String index : config.getConfigurationSection("block").getKeys(false)) {
            if (config.getString("block." + index + ".utility").equalsIgnoreCase("barrier")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description")
                );
                barrierBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("confirm")) {
                confirmBlocks.put(Integer.valueOf(index), new Confirm(null,  Material.getMaterial(config.getString("block." + index + ".material")), config.getBoolean("block." + index + ".value")));
            }
        }
    }

    public List<String> getDescription() {
        return description;
    }

    public String getNameGui() {
        return nameGui;
    }

    public Integer getSize() {
        return size;
    }

    public List<Barrier> getBarrierBlocks() {
        return barrierBlocks;
    }

    public Map<Integer, Confirm> getConfirmBlocks() {
        return confirmBlocks;
    }

    public String getTitleTrue() {
        return titleTrue;
    }

    public String getTitleFalse() {
        return titleFalse;
    }
}
