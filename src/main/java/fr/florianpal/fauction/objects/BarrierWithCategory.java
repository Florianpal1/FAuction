package fr.florianpal.fauction.objects;

import lombok.Getter;
import org.bukkit.Material;

import java.util.List;

@Getter
public class BarrierWithCategory extends Barrier {

    private final Category category;

    public BarrierWithCategory(int index, Material material, String title, List<String> description, String texture, int customModelData, Category category) {
        super(index, material, title, description, null, texture, customModelData);

        this.category = category;
    }

    public BarrierWithCategory(int index, Material material, String title, List<String> description, BarrierWithCategory remplacement, String texture, int customModelData, Category category) {
        super(index, material, title, description, null, remplacement, texture, customModelData);

        this.category = category;
    }

}
