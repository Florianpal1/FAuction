package fr.florianpal.fauction.objects;

import fr.florianpal.fauction.utils.SerializationUtil;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

@Getter
public class BidHistoric {

    private final int id;

    private final UUID sellerUuid;

    private final String sellerName;

    private final UUID buyerUuid;

    private final String buyerName;

    private final ItemStack itemStack;

    private final double startPrice;

    private final double finalPrice;

    private final Date startDate;

    private final Date endDate;

    public BidHistoric(int id, UUID sellerUuid, String sellerName, UUID buyerUuid, String buyerName, byte[] item,
                        double startPrice, double finalPrice, long startDate, long endDate) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        try {
            this.itemStack = SerializationUtil.deserialize(item);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.startPrice = startPrice;
        this.finalPrice = finalPrice;
        this.startDate = new Date(startDate);
        this.endDate = new Date(endDate);
    }
}
