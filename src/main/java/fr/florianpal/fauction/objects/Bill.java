package fr.florianpal.fauction.objects;

import java.util.Date;
import java.util.UUID;

public class Bill extends Auction {

    private UUID playerBidderUuid;

    private String playerBidderName;

    private double bet;

    private Date betDate;

    public Bill(int id, UUID playerUuid, String playerName, double price, byte[] item, long date) {
        super(id, playerUuid, playerName, price, item, date);
    }

    public Bill(int id, UUID playerUuid, String playerName, double price, byte[] item, long date, UUID playerBidderUuid, String playerBidderName, double bet, long betDate) {
        super(id, playerUuid, playerName, price, item, date);
        this.playerBidderUuid = playerBidderUuid;
        this.playerBidderName = playerBidderName;
        this.bet = bet;
        this.betDate = new Date(betDate);
    }

    public UUID getPlayerBidderUuid() {
        return playerBidderUuid;
    }

    public String getPlayerBidderName() {
        return playerBidderName;
    }

    public double getBet() {
        return bet;
    }

    public Date getBetDate() {
        return betDate;
    }
}
