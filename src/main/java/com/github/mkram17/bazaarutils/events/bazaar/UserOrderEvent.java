package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Fired after a source successfully mutates {@link UserOrdersStorage}.
 *
 * <p>Each subtype maps to exactly one storage transition. Sources fire the
 * appropriate subtype immediately after persisting — consumers never need to
 * poll storage or diff state themselves.
 */
public sealed class UserOrderEvent extends SkyBlockEvent permits
        UserOrderEvent.Placed,
        UserOrderEvent.PartiallyFilled,
        UserOrderEvent.Filled,
        UserOrderEvent.Claimed,
        UserOrderEvent.Cancelled,
        UserOrderEvent.Flipped {

    @Getter
    @NotNull
    private final Order order;

    protected UserOrderEvent(@NotNull Order order) {
        this.order = order;
    }

    /**
     * A new order entered storage
     */
    public static final class Placed extends UserOrderEvent {
        public Placed(Order order) {
            super(order);
        }
    }

    /**
     * An order transitioned to {@link OrderStatus.Partial}.
     */
    public static final class PartiallyFilled extends UserOrderEvent {
        public PartiallyFilled(Order order) {
            super(order);
        }
    }

    /**
     * An order transitioned to {@link OrderStatus.Filled}.
     */
    public static final class Filled extends UserOrderEvent {
        public Filled(Order order) {
            super(order);
        }
    }

    /**
     * Coins or items were claimed — fired by {@code OrderClaimedDataSource}.
     * {@link Order#status()} will be {@link OrderStatus.Claimed}
     * when the claim was terminal, or the prior status when partial.
     */
    public static final class Claimed extends UserOrderEvent {
        public Claimed(Order order) {
            super(order);
        }
    }

    /**
     * An order was cancelled.
     */
    public static final class Cancelled extends UserOrderEvent {
        public Cancelled(Order order) {
            super(order);
        }
    }

    /**
     * A flip was handled.
     */
    public static final class Flipped extends UserOrderEvent {
        /** The new SELL order that was placed as a result of the flip. */
        @Getter
        private final Order newOrder;

        /**
         * @param flipped  the {@code BUY} order whose fill was claimed to fund the flip
         * @param newOrder the {@code SELL} offer synthesized from the flip
         */
        public Flipped(Order flipped, Order newOrder) {
            super(flipped);   // parent getOrder() → claimedBuy
            this.newOrder = newOrder;
        }
    }
}