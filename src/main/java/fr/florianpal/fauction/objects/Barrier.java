package fr.florianpal.fauction.objects;

import lombok.Getter;
import org.bukkit.Material;

import java.util.List;

@Getter
public class Barrier {

    private final int index;

    private final Material material;

    private final String title;

    private final List<String> description;

    private final String value;

    private Barrier remplacement;

    private final String texture;

    private final int customModelData;

    public Barrier(int index, Material material, String title, List<String> description, String value, String texture, int customModelData) {
        this.index = index;
        this.material = material;
        this.title = title;
        this.description = description;
        this.value = value;
        this.texture = texture;
        this.customModelData = customModelData;
    }

    public Barrier(int index, Material material, String title, List<String> description, String value, Barrier remplacement, String texture, int customModelData) {
        this.index = index;
        this.material = material;
        this.title = title;
        this.description = description;
        this.value = value;
        this.remplacement = remplacement;
        this.texture = texture;
        this.customModelData = customModelData;
    }
}
