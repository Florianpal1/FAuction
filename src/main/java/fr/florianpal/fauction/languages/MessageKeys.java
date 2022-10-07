
/*
 * Copyright (C) 2022 Florianpal
 *
 * This program is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses/>.
 *
 * Last modification : 07/01/2022 23:07
 *
 *  @author Florianpal.
 */

package fr.florianpal.fauction.languages;

import co.aikar.locales.MessageKey;
import co.aikar.locales.MessageKeyProvider;

public enum MessageKeys implements MessageKeyProvider {
    NO_AUCTION,
    AUCTION_OPEN,
    AUCTION_ADD_SUCCESS,
    REMOVE_AUCTION_SUCCESS,
    BUY_YOUR_ITEM,
    ITEM_AIR,
    NO_HAVE_MONEY,
    BUY_AUCTION_SUCCESS,
    AUCTION_EXPIRE,
    AUCTION_ALREADY_SELL,
    NEGATIVE_PRICE,
    MAX_AUCTION,
    AUCTION_EXPIRE_DROP,
    BUY_AUCTION_CANCELLED,
    MIN_PRICE,
    AUCTION_RELOAD,

    REMOVE_EXPIRE_SUCCESS,

    MAX_BILL,
    BILL_ADD_SUCCESS,
    NO_BILL,

    BILL_ALREADY_SELL,

    MAKE_OFFER_BILL_SUCCESS,

    BUY_BILL_CANCELLED,
    REMOVE_BILL_SUCCESS,

    DATABASEERROR;

    private static final String PREFIX = "fauction";

    private final MessageKey key = MessageKey.of(PREFIX + "." + this.name().toLowerCase());

    public MessageKey getMessageKey() {
        return key;
    }
}
