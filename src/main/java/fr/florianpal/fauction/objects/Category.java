package fr.florianpal.fauction.objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public class Category {

    private final String id;

    private final String displayName;

    private final List<String> materials;

    public Category(String id, String displayName, List<String> materials) {
        this.id = id;
        this.displayName = displayName;
        this.materials = materials;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getMaterialsString() {
        return materials;
    }

    public List<Material> getMaterials() {
        return materials.stream().map(Material::getMaterial).collect(Collectors.toList());
    }

    public boolean containsAll() {
        return materials.contains("ALL");
    }

    public boolean containsEnchanted() {
        return materials.contains("ENCHANTED");
    }

    public boolean containsWeapons() {
        return materials.contains("WEAPONS");
    }

    public boolean containsTools() {
        return materials.contains("TOOLS");
    }

    public boolean containsArmor() {
        return materials.contains("ARMOR");
    }

    public boolean containsBlocks() {
        return materials.contains("BLOCKS");
    }

    public boolean containsFood() {
        return materials.contains("FOOD");
    }

    public boolean containsPotions() {
        return materials.contains("POTIONS");
    }

    public boolean containsMisc() {
        return materials.contains("MISC");
    }

    public boolean containsCustom() {
        return materials.contains("CUSTOM");
    }
}
