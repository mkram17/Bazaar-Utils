package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;

//TODO use this instead of OutdatedOrderEvent
public record BazaarChatEvent<T extends OrderInfoContainer>(
        BazaarEventTypes type,
        T order
) {
    public enum BazaarEventTypes {
        ORDER_CREATED,
        ORDER_CANCELLED,
        ORDER_FILLED,
        ORDER_CLAIMED,
        ORDER_FLIPPED,
        INSTA_SELL,
        INSTA_BUY,
    }
}
