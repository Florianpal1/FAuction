package fr.florianpal.fauction.languages;


public enum MessageKeys {
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

    /**
     * The route of the key in the language file. The prefix and the lower-cased name are kept
     * exactly as ACF built them : the four language files are deployed on the servers of the users
     * and their keys depend on it.
     */
    private final String key = PREFIX + "." + this.name().toLowerCase();

    public String getKey() {
        return key;
    }
}
