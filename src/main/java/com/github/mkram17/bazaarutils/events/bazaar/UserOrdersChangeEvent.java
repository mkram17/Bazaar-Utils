package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;
import meteordevelopment.orbit.ICancellable;

/**
 * Event fired when the user's bazaar orders list changes.
 * <p>
 * This event is triggered whenever a bazaar order is added, removed, or updated in the user's
 * tracked orders list. It allows listeners to react to changes in the player's active orders.
 * </p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * {@code
 * @EventHandler
 * public void onOrderChange(UserOrdersChangeEvent event) {
 *     switch (event.getChangeType()) {
 *         case ADD -> handleNewOrder(event.getOrder());
 *         case REMOVE -> handleRemovedOrder(event.getOrder());
 *         case UPDATE -> handleUpdatedOrder(event.getOrder());
 *     }
 * }
 * }
 * </pre>
 * 
 * @see Order
 * @see ChangeTypes
 */
@AllArgsConstructor
public class UserOrdersChangeEvent implements ICancellable {

    /**
     * Enumeration of possible change types for user orders.
     */
    public enum ChangeTypes {
        /** A new order was added to the tracked orders */
        ADD,
        /** An existing order was removed from the tracked orders */
        REMOVE,
        /** An existing order was updated (e.g., volume or price changed) */
        UPDATE
    }
    
    /**
     * The type of change that occurred.
     */
    @Getter
    private ChangeTypes changeType;
    
    /**
     * The bazaar order that was affected by the change.
     */
    @Getter
    private Order order;

    @Override
    public void setCancelled(boolean cancelled) {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}