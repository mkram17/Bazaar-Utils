package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Event fired when a bazaar-related chat message is received and parsed.
 * <p>
 * This event is triggered when the mod detects and parses a bazaar-related message from the game chat,
 * such as order creation, cancellation, filling, claiming, or instant transactions. The event contains
 * the parsed order information and the type of bazaar action that occurred.
 * </p>
 * 
 * @see OrderInfo
 * @see BazaarEventTypes
 */
//TODO use this instead of OutdatedOrderEvent
public class BazaarChatEvent<T extends OrderInfo> extends SkyBlockEvent {
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

    @Getter
    public final BazaarEventTypes type;

    @Getter
    public final T order;

    public BazaarChatEvent(BazaarChatEvent.BazaarEventTypes type, T order) {
        this.type = type;
        this.order = order;
    }
}
