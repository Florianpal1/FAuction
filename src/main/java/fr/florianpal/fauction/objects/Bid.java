package fr.florianpal.fauction.objects;

import fr.florianpal.fauction.utils.SerializationUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

@Getter
public class Bid {

    private final int id;

    private final UUID sellerUuid;

    private final String sellerName;

    private final ItemStack itemStack;

    private final double startPrice;

    @Setter
    private double currentPrice;

    @Setter
    private UUID currentBidderUuid;

    @Setter
    private String currentBidderName;

    private final Date startDate;

    private final Date endDate;

    public Bid(int id, UUID sellerUuid, String sellerName, byte[] item, double startPrice, double currentPrice,
                UUID currentBidderUuid, String currentBidderName, long startDate, long endDate) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        try {
            this.itemStack = SerializationUtil.deserialize(item);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.startPrice = startPrice;
        this.currentPrice = currentPrice;
        this.currentBidderUuid = currentBidderUuid;
        this.currentBidderName = currentBidderName;
        this.startDate = new Date(startDate);
        this.endDate = new Date(endDate);
    }

    public boolean hasBidder() {
        return currentBidderUuid != null;
    }
}
