package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
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
 */
public abstract sealed class BazaarChatEvent extends SkyBlockEvent permits BazaarChatEvent.OrderResolvable, BazaarChatEvent.BuyOrderCancelled {
    @Getter
    public final long receivedAt;

    protected BazaarChatEvent(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public abstract static sealed class OrderResolvable<T extends OrderInfo> extends BazaarChatEvent
            permits BuyOrderCreated, SellOfferCreated,
            SellOfferCancelled,
            BuyOrderFilled, SellOfferFilled,
            BuyOrderClaimed, SellOfferClaimed,
            BuyOrderFlipped,
            InstantBuy, InstantSell {
        @Getter
        @NotNull
        public final T order;

        protected OrderResolvable(@NotNull T order, long receivedAt) {
            super(receivedAt);
            this.order = order;
        }
    }

    /** A new buy order was placed. */
    public static final class BuyOrderCreated extends OrderResolvable<OrderInfo> {
        public BuyOrderCreated(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /** A new sell offer was placed. */
    public static final class SellOfferCreated extends OrderResolvable<OrderInfo> {
        public SellOfferCreated(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /**
     * A buy order was cancelled.
     * <p>
     * Hypixel's cancellation message carries only the refunded coins.
     * </p>
     */
    public static final class BuyOrderCancelled extends BazaarChatEvent {
        @Getter
        public final double refundedCoins;

        public BuyOrderCancelled(double refundedCoins, long receivedAt) {
            super(receivedAt);
            this.refundedCoins = refundedCoins;
        }
    }

    /** A sell offer was cancelled; carries the returned item info. */
    public static final class SellOfferCancelled extends OrderResolvable<OrderInfo> {
        public SellOfferCancelled(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /** A buy order was completely filled. */
    public static final class BuyOrderFilled extends OrderResolvable<OrderInfo> {
        public BuyOrderFilled(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /** A sell offer was completely filled. */
    public static final class SellOfferFilled extends OrderResolvable<OrderInfo> {
        public SellOfferFilled(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /** Items from a buy order were claimed. */
    public static final class BuyOrderClaimed extends OrderResolvable<OrderInfo> {
        public BuyOrderClaimed(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /** Coins from a sell offer were claimed. */
    public static final class SellOfferClaimed extends OrderResolvable<OrderInfo> {
        public SellOfferClaimed(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /** A buy order was flipped. */
    public static final class BuyOrderFlipped extends OrderResolvable<OrderInfo> {
        public BuyOrderFlipped(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /** Items were instantly purchased from sell offers. */
    public static final class InstantBuy extends OrderResolvable<OrderInfo> {
        public InstantBuy(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }

    /** Items were instantly sold to buy orders. */
    public static final class InstantSell extends OrderResolvable<OrderInfo> {
        public InstantSell(@NotNull OrderInfo order, long receivedAt) {
            super(order, receivedAt);
        }
    }
}