package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;

/**
 * Event fired when a bazaar-related chat message is received and parsed.
 * <p>
 * This event is triggered when the mod detects and parses a bazaar-related message from the game chat,
 * such as order creation, cancellation, filling, claiming, or instant transactions. The event contains
 * the parsed order information and the type of bazaar action that occurred.
 * </p>
 *
 * 
 * @param <T> the type of order information container, must extend OrderInfoContainer
 * @param type the type of bazaar event that occurred
 * @param order the order information associated with the event
 * 
 * @see OrderInfo
 * @see BazaarEventTypes
 */
//TODO use this instead of OutdatedOrderEvent
public record BazaarChatEvent<T extends OrderInfo>(
        BazaarEventTypes type,
        T order
) {
    /**
     * Enumeration of bazaar event types that can be detected from chat messages.
     */
    public enum BazaarEventTypes {
        /** A new buy or sell order was created */
        ORDER_CREATED,
        /** An existing order was canceled */
        ORDER_CANCELLED,
        /** An order was completely filled */
        ORDER_FILLED,
        /** Coins or items from a filled order were claimed */
        ORDER_CLAIMED,
        /** An order's price was flipped/updated */
        ORDER_FLIPPED,
        /** Items were instantly sold to buy orders */
        INSTA_SELL,
        /** Items were instantly bought from sell offers */
        INSTA_BUY,
    }
}
