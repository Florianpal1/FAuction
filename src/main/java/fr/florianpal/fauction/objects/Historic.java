package fr.florianpal.fauction.objects;

import lombok.Getter;

import java.util.Date;
import java.util.UUID;

@Getter
public class Historic extends Auction {

    private final UUID playerBuyerUUID;

    private final String playerBuyerName;

    private final Date buyDate;

    public Historic(int id, UUID playerUUID, String playerName, UUID playerBuyerUUID, String playerBuyerName, double price, byte[] item, long date, long buyDate) {
        super(id, playerUUID, playerName, price, item, date);
        this.playerBuyerUUID = playerBuyerUUID;
        this.playerBuyerName = playerBuyerName;
        this.buyDate = new Date(buyDate);
    }
}
