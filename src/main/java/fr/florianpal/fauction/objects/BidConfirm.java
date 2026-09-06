package fr.florianpal.fauction.objects;

import lombok.Getter;
import org.bukkit.Material;

@Getter
public class BidConfirm {

    private final int index;

    private final Bid bid;

    private final Material material;

    private final String texture;

    private final int customModelData;

    private final boolean value;

    public BidConfirm(int index, Bid bid, Material material, boolean value, String texture, int customModelData) {
        this.index = index;
        this.bid = bid;
        this.material = material;
        this.texture = texture;
        this.customModelData = customModelData;
        this.value = value;
    }
}
