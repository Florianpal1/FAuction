package fr.florianpal.fauction.objects;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

@Getter
public class Confirm {

    private final int index;

    @Setter
    private Auction auction;

    @Setter
    private Material material;

    private final String texture;

    private final int customModelData;

    private final boolean value;

    public Confirm(int index, Auction auction, Material material, boolean value, String texture, int customModelData) {
        this.index = index;
        this.auction = auction;
        this.material = material;
        this.texture = texture;
        this.customModelData = customModelData;
        this.value = value;
    }
}
