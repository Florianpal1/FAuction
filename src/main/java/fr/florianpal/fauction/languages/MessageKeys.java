package fr.florianpal.fauction.languages;


import co.aikar.locales.MessageKey;
import co.aikar.locales.MessageKeyProvider;

public enum MessageKeys implements MessageKeyProvider {
    NO_AUCTION,
    AUCTION_OPEN,
    AUCTION_ADD_SUCCESS,

    BUY_AUCTION_TARGET_SUCCESS,
    REMOVE_AUCTION_SUCCESS,
    BUY_YOUR_ITEM,
    ITEM_AIR,
    SEARCH_AIR,
    NO_HAVE_MONEY,
    BUY_AUCTION_SUCCESS,
    AUCTION_EXPIRE,
    AUCTION_ALREADY_SELL,
    NEGATIVE_PRICE,
    MAX_AUCTION,
    AUCTION_EXPIRE_DROP,
    BUY_AUCTION_CANCELLED,
    MIN_PRICE,

    MAX_PRICE,

    SPAM,

    AUCTION_RELOAD,

    CLEAR_CACHE,

    REMOVE_EXPIRE_SUCCESS,

    ITEM_BLACKLIST,

    TRANSFERT_BDD,

    AUCTION_PURGE,

    MIGRATE,

    DATABASEERROR;

    private static final String PREFIX = "fauction";

    private final MessageKey key = MessageKey.of(PREFIX + "." + this.name().toLowerCase());

    public MessageKey getMessageKey() {
        return key;
    }
}
