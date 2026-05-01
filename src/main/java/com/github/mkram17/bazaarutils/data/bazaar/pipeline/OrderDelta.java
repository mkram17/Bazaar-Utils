package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderSlotPosition;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import org.jetbrains.annotations.Nullable;

public sealed interface OrderDelta permits
        OrderDelta.None,
        OrderDelta.BookOnly,
        OrderDelta.Place,
        OrderDelta.Update,
        OrderDelta.Evict,
        OrderDelta.Swap,
        OrderDelta.PriceCorrection,
        OrderDelta.Reanchor {

    /**
     * The book mutation for this delta. Defaults to {@link BookMutation#NONE} so
     * {@code commitAll} can compose all mutations in a single reduction pass without
     * switching on type.
     */
    default BookMutation mutation() {
        return BookMutation.NONE;
    }

    /** No-op. Returned when a source determines no commit is needed. */
    record None() implements OrderDelta {}

    /**
     * Book mutation only — no storage write, no {@link UserOrderEvent}.
     * Used by instant deals, which consume book volume but leave no tracked order.
     */
    record BookOnly(String productId, BookMutation mutation) implements OrderDelta {}

    /** New order enters storage. Triggers reindex and {@link UserOrderEvent.Placed}. */
    record Place(Order order, BookMutation mutation) implements OrderDelta {
        public String productId() {
            return order.productId();
        }

        /**
         * Fires {@link UserOrderEvent.Filled} or {@link UserOrderEvent.PartiallyFilled}
         * when the placed order already has fill progress, null otherwise.
         */
        @Nullable
        public UserOrderEvent initialFillEvent() {
            return switch (order.status()) {
                case OrderStatus.Filled ignored -> new UserOrderEvent.Filled(order);
                case OrderStatus.Partial ignored -> new UserOrderEvent.PartiallyFilled(order, order.filledAmount());
                default -> null;
            };
        }
    }

    /** Existing tracked order mutated — fill advance or claim advance. */
    record Update(
            Order before,
            Order after,
            BookMutation mutation,
            UpdateKind kind
    ) implements OrderDelta {
        public String productId() {
            return before.productId();
        }

        /** Convenience factory for fill transitions. */
        public static Update fill(Order before, Order after, BookMutation mutation) {
            return new Update(before, after, mutation, UpdateKind.FILL);
        }

        /** Convenience factory for claim transitions. Book mutation is always NONE for claims. */
        public static Update claim(Order before, Order after) {
            return new Update(before, after, BookMutation.NONE, UpdateKind.CLAIM);
        }

        public enum UpdateKind {
            /**
             * Fill advanced. Fires {@link UserOrderEvent.Filled} on full completion,
             * {@link UserOrderEvent.PartiallyFilled} otherwise.
             */
            FILL {
                @Override
                public UserOrderEvent getEvent(Order before, Order after) {
                    return after.status() instanceof OrderStatus.Filled
                            ? new UserOrderEvent.Filled(after)
                            : new UserOrderEvent.PartiallyFilled(after, after.filledAmount() - before.filledAmount());
                }
            },

            /**
             * Claimed volume advanced. Always fires {@link UserOrderEvent.Claimed}, regardless
             * of terminal state.
             */
            CLAIM {
                @Override
                public UserOrderEvent getEvent(Order before, Order after) {
                    return new UserOrderEvent.Claimed(after, after.claimedAmount() - before.claimedAmount());
                }
            };

            public abstract UserOrderEvent getEvent(Order before, Order after);
        }
    }

    /** Order evicted from storage. Decrements the book and fires {@link UserOrderEvent.Cancelled}. */
    record Evict(Order order, BookMutation mutation) implements OrderDelta {
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
    record Swap(
            Order buyBefore,
            Order buyAfter,
            Order newSell,
            BookMutation mutation,
            double profitPerUnit   // carried for logging only — not applied to book
    ) implements OrderDelta {
        public String productId() {
            return buyBefore.productId();
        }
    }

    /**
     * Screen-observed price correction. Remutates the book (decrement stale level,
     * place corrected level), replaces the order in-place without reindex, and fires
     * no {@link UserOrderEvent} — the
     * book mutation and resolution log line capture the change.
     */
    record PriceCorrection(Order before, Order after, BookMutation mutation) implements OrderDelta {
        public String productId() {
            return before.productId();
        }
    }

    /**
     * Pure slot reanchor — the order's {@link OrderSlotPosition} changed on screen
     * but no state was mutated (no fill advance, no claim advance, no price correction).
     *
     * <p>No book mutation, no {@link UserOrderEvent}.
     * Emitted into {@code deltas} solely to force {@code commitAll}'s
     * {@code !deltas.isEmpty()} guard past the early-return so that {@code after} —
     * carrying the updated {@link OrderSlotPosition} — is written to storage.
     */
    record Reanchor(Order before, Order after) implements OrderDelta {
        public String productId() {
            return before.productId();
        }
    }
}