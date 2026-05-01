package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderSlotPosition;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The complete, already-resolved outcome of one source's handling of a single Bazaar
 * interaction — what changed, and how that change should be written, mutated, and
 * announced.
 *
 * <p>Every variant is generic in its {@link BookMutation}'s origin type, but each one
 * describes something confirmed about one order's own state, which in practice means
 * that origin is always a {@link BazaarDataOrigin.UserPositionEvent}. A market read's
 * own book effect has no before/after order pair and fires no {@link UserOrderEvent},
 * so it's applied directly by whichever source produced it rather than wrapped here.
 *
 * <p>{@link Update.UpdateKind#getEvent} is the one piece reused outside this type's own
 * commit paths — {@link FillInference} calls it directly for an inferred fill.
 */
public sealed interface OrderDelta<O extends BazaarDataOrigin> permits
        OrderDelta.None,
        OrderDelta.BookOnly,
        OrderDelta.Place,
        OrderDelta.Update,
        OrderDelta.Evict,
        OrderDelta.Swap,
        OrderDelta.PriceCorrection,
        OrderDelta.Reanchor {

    /**
     * The book mutation this delta carries. Defaults to
     * {@link BookMutation#none()}, so a commit path folding every delta's
     * mutation into one chain never needs to switch on variant type first.
     */
    default BookMutation<O> mutation() {
        return BookMutation.none();
    }

    /** No-op. Returned when a source determines no commit is needed. */
    record None<O extends BazaarDataOrigin>() implements OrderDelta<O> {}

    /**
     * Book mutation only — no storage write, no {@link UserOrderEvent}.
     * Used by instant deals, which consume book volume but leave no tracked order.
     */
    record BookOnly<O extends BazaarDataOrigin>(String productId, BookMutation<O> mutation) implements OrderDelta<O> {}

    /** New order enters storage. Triggers reindex and {@link UserOrderEvent.Placed}. */
    record Place<O extends BazaarDataOrigin>(Order order, BookMutation<O> mutation) implements OrderDelta<O> {
        public String productId() {
            return order.productId();
        }

        /**
         * Fires {@link UserOrderEvent.Filled} or {@link UserOrderEvent.PartiallyFilled}
         * when the placed order already has fill progress, {@code null} otherwise.
         */
        @Nullable
        public UserOrderEvent initialFillEvent(@NotNull ProfileKey key) {
            return switch (order.status()) {
                case OrderStatus.Filled ignored -> new UserOrderEvent.Filled(order, key);
                case OrderStatus.Partial ignored -> new UserOrderEvent.PartiallyFilled(order, order.filledAmount(), key);
                default -> null;
            };
        }
    }

    /** Existing tracked order mutated — fill advance, expiry, or claim advance. */
    record Update<O extends BazaarDataOrigin>(
            Order before,
            Order after,
            BookMutation<O> mutation,
            UpdateKind kind
    ) implements OrderDelta<O> {
        public String productId() {
            return before.productId();
        }

        /** Convenience factory for a fill transition. */
        public static <O extends BazaarDataOrigin> Update<O> fill(Order before, Order after, BookMutation<O> mutation) {
            return new Update<O>(before, after, mutation, UpdateKind.FILL);
        }

        /** Convenience factory for an expiry transition. */
        public static <O extends BazaarDataOrigin> Update<O> expiry(Order before, Order after, BookMutation<O> mutation) {
            return new Update<O>(before, after, mutation, UpdateKind.EXPIRY);
        }

        /** Convenience factory for a claim transition. Always {@link BookMutation#none()} — a claim never touches the book. */
        public static <O extends BazaarDataOrigin> Update<O> claim(Order before, Order after) {
            return new Update<O>(before, after, BookMutation.none(), UpdateKind.CLAIM);
        }

        /**
         * Which field advanced, and how to construct the {@link UserOrderEvent} that
         * announces it. Takes an explicit {@link ProfileKey} rather than assuming any
         * one profile, since a caller checking transitions across every known profile
         * — not just whichever one is active — needs to supply the right one.
         */
        public enum UpdateKind {
            /**
             * Fill advanced. Fires {@link UserOrderEvent.Filled} on full completion,
             * {@link UserOrderEvent.PartiallyFilled} otherwise.
             */
            FILL {
                @Override
                public UserOrderEvent getEvent(Order before, Order after, ProfileKey key) {
                    // Looks through an Expired wrapper — an Expired(Filled) `after`
                    // must still fire Filled, not PartiallyFilled. The only call site
                    // that could ever pass an Expired-wrapped `after` at all is
                    // OrdersScreenDataSource.getMutationEvents (FillInference's own
                    // withFill()-derived `after` values are always bare); this stays
                    // correct for that call site's existing, unaffected bare case too.
                    boolean fullyFilled = after.status().effectiveNonTerminal()
                            .filter(ns -> ns instanceof OrderStatus.Filled)
                            .isPresent();

                    return fullyFilled
                            ? new UserOrderEvent.Filled(after, key)
                            : new UserOrderEvent.PartiallyFilled(after, after.filledAmount() - before.filledAmount(), key);
                }
            },

            /** Order transitioned to {@link OrderStatus.Expired}. Fires {@link UserOrderEvent.Expired}. */
            EXPIRY {
                @Override
                public UserOrderEvent getEvent(Order before, Order after, ProfileKey key) {
                    return new UserOrderEvent.Expired(after, key);
                }
            },

            /**
             * Claimed volume advanced. Always fires {@link UserOrderEvent.Claimed}, regardless
             * of terminal state.
             */
            CLAIM {
                @Override
                public UserOrderEvent getEvent(Order before, Order after, ProfileKey key) {
                    return new UserOrderEvent.Claimed(after, after.claimedAmount() - before.claimedAmount(), key);
                }
            };

            public abstract UserOrderEvent getEvent(Order before, Order after, ProfileKey key);
        }
    }

    /**
     * Order evicted from storage, carrying whatever book decrement accompanies its
     * removal — {@link BookMutation#none()} when the book side was already cleared by
     * an earlier event. Fires {@link UserOrderEvent.Cancelled} either way.
     */
    record Evict<O extends BazaarDataOrigin>(Order order, BookMutation<O> mutation) implements OrderDelta<O> {
        public String productId() {
            return order.productId();
        }
    }

    /**
     * Flip: buy order claimed and new sell offer inserted atomically. The buy book
     * needs no mutation — the buy level was already decremented when the fill was
     * recorded. {@code mutation} places the new sell level. Fires
     * {@link UserOrderEvent.Flipped}.
     */
    record Swap<O extends BazaarDataOrigin>(
            Order buyBefore,
            Order buyAfter,
            Order newSell,
            BookMutation<O> mutation,
            double profitPerUnit
    ) implements OrderDelta<O> {
        public String productId() {
            return buyBefore.productId();
        }
    }

    /**
     * Screen-observed price correction: replaces the stored order in place, with no
     * reindex and no {@link UserOrderEvent} — only the book mutation and a log line
     * record the change.
     */
    record PriceCorrection<O extends BazaarDataOrigin>(Order before, Order after, BookMutation<O> mutation) implements OrderDelta<O> {
        public String productId() {
            return before.productId();
        }
    }

    /**
     * A matched order's screen-confirmed {@link OrderSlotPosition} changed with no
     * other state advancing. No book mutation, no {@link UserOrderEvent} — {@code after}
     * is written through purely to keep the stored slot in step with the screen.
     */
    record Reanchor(Order before, Order after) implements OrderDelta {
        public String productId() {
            return before.productId();
        }
    }
}