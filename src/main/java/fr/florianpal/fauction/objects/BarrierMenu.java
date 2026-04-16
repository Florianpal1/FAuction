package fr.florianpal.fauction.objects;

import lombok.Getter;
import org.bukkit.Material;

import java.util.List;

@Getter
public class BarrierMenu extends Barrier {

    private final String id;

    public BarrierMenu(int index, Material material, String title, List<String> description, String texture, int customModelData, String id) {
        super(index, material, title, description, null, texture, customModelData);

        this.id = id;
    }

    public BarrierMenu(int index, Material material, String title, List<String> description, BarrierMenu remplacement, String texture, int customModelData, String id) {
        super(index, material, title, description, null, remplacement, texture, customModelData);

        this.id = id;
    }

}
