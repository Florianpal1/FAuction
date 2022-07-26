
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

import org.bukkit.Material;

import java.util.List;

public class Barrier {
    private final int index;
    private final Material material;
    private final String title;
    private final List<String> description;
    private Barrier remplacement;
    public Barrier(int index, Material material, String title, List<String> description) {
        this.index = index;
        this.material = material;
        this.title = title;
        this.description = description;
    }

    public Barrier(int index, Material material, String title, List<String> description, Barrier remplacement) {
        this.index = index;
        this.material = material;
        this.title = title;
        this.description = description;
        this.remplacement = remplacement;
    }

    public int getIndex() {
        return index;
    }

    public Material getMaterial() {
        return material;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getDescription() {
        return description;
    }

    public Barrier getRemplacement() {
        return remplacement;
    }
}
