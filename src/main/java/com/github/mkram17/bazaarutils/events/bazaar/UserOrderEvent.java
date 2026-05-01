package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Fired after a source successfully mutates {@link UserOrdersStorage}.
 *
 * <p>Each subtype maps to exactly one storage transition. Sources fire the
 * appropriate subtype immediately after persisting — consumers never need to
 * poll storage or diff state themselves.
 *
 * <p>Every instance carries the {@link ProfileKey} it was fired for — the
 * book that produces most of these mutations has no notion of "active
 * profile," so the same event can now legitimately be fired for a known
 * position other than whichever one is on screen.
 */
public sealed class UserOrderEvent extends SkyBlockEvent permits
        UserOrderEvent.Placed,
        UserOrderEvent.PartiallyFilled,
        UserOrderEvent.Filled,
        UserOrderEvent.Claimed,
        UserOrderEvent.Cancelled,
        UserOrderEvent.Expired,
        UserOrderEvent.Flipped {
    /** The order's state after the change this event reports. */
    @Getter
    @NotNull
    private final Order order;

    /** Which profile's storage this mutation happened in. */
    @Getter
    @NotNull
    private final ProfileKey profileKey;

    protected UserOrderEvent(@NotNull Order order, @NotNull ProfileKey profileKey) {
        this.order = order;
        this.profileKey = profileKey;
    }

    /** A new order entered storage — either placed by the player or synthesized from an untracked observation. */
    public static final class Placed extends UserOrderEvent {
        public Placed(Order order, ProfileKey profileKey) {
            super(order, profileKey);
        }
    }

    /** An order advanced toward completion without finishing it — {@link Order#status()} is {@link OrderStatus.Partial}. */
    public static final class PartiallyFilled extends UserOrderEvent {
        /** Units filled in this step, not the order's running total. */
        @Getter
        private final int filledDelta;

        public PartiallyFilled(Order order, int filledDelta, ProfileKey profileKey) {
            super(order, profileKey);
            this.filledDelta = filledDelta;
        }
    }

    /** An order finished filling — {@link Order#status()} is {@link OrderStatus.Filled}. */
    public static final class Filled extends UserOrderEvent {
        public Filled(Order order, ProfileKey profileKey) {
            super(order, profileKey);
        }
    }

    /**
     * Filled items or coins were retrieved. {@link Order#status()} is
     * {@link OrderStatus.Claimed} when this claim finished the order off;
     * otherwise the order's prior status carries through unchanged, since a
     * partial claim on a still-filling or still-filled order does not by
     * itself advance its lifecycle.
     */
    public static final class Claimed extends UserOrderEvent {
        /** Units or coin batches claimed in this step, not the running total. */
        @Getter
        private final int claimedDelta;

        public Claimed(Order order, int claimedDelta, ProfileKey profileKey) {
            super(order, profileKey);
            this.claimedDelta = claimedDelta;
        }
    }

    /** An order was cancelled and left storage. */
    public static final class Cancelled extends UserOrderEvent {
        public Cancelled(Order order, ProfileKey profileKey) {
            super(order, profileKey);
        }
    }

    /**
     * An order lapsed its Hypixel lifetime with volume still unfilled. The
     * order remains live — the player must claim any filled volume, then
     * cancel via Options, to recover the coins or items still held in
     * escrow for the unfilled remainder.
     */
    public static final class Expired extends UserOrderEvent {
        public Expired(@NotNull Order order, ProfileKey profileKey) {
            super(order, profileKey);
        }
    }

    /**
     * A completed buy order was claimed and its proceeds placed as a new
     * sell offer in one atomic step. {@link #getOrder()} is the buy order
     * <em>after</em> its claim, not before — the flip claims it in full as
     * part of funding the new offer.
     */
    public static final class Flipped extends UserOrderEvent {
        /** The sell offer synthesized from the flip's proceeds. */
        @Getter
        private final Order newOrder;

        /**
         * @param flipped  the buy order, already reflecting the claim the flip performed
         * @param newOrder the sell offer placed with the claimed proceeds
         */
        public Flipped(Order flipped, Order newOrder, ProfileKey profileKey) {
            super(flipped, profileKey);
            this.newOrder = newOrder;
        }
    }
}