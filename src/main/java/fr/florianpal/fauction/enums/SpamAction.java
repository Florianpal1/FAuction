package fr.florianpal.fauction.enums;

import fr.florianpal.fauction.objects.SpamLimit;
import lombok.Getter;

/**
 * Kind of action protected against packet spamming. Each one has its own budget, so navigating in a
 * gui never consumes the budget of a purchase.
 */
@Getter
public enum SpamAction {

    /**
     * Click on an item of a gui (open a confirmation, cancel an auction, take back an expired item).
     */
    INTERACT("interact", new SpamLimit(8, 4)),

    /**
     * Command opening a gui (/ah, /ah search).
     */
    COMMAND("command", new SpamLimit(6, 2)),

    /**
     * Action moving items or money (purchase confirmation, /ah sell).
     */
    TRANSACTION("transaction", new SpamLimit(4, 2));

    private final String configKey;

    private final SpamLimit defaultLimit;

    SpamAction(String configKey, SpamLimit defaultLimit) {
        this.configKey = configKey;
        this.defaultLimit = defaultLimit;
    }
}
