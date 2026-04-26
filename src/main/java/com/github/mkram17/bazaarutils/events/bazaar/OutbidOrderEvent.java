package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import lombok.Getter;
import meteordevelopment.orbit.ICancellable;

/**
 * Event fired when a bazaar order is outbid or becomes competitive again.
 * <p>
 * This event is triggered when the player's order in the bazaar is no longer the best offer (outbid)
 * or when it becomes competitive again. This allows the mod to notify the player about their order status.
 * </p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * {@code
 * @EventHandler
 * public void onOutbid(OutbidOrderEvent event) {
 *     if (event.isOutbid()) {
 *         // Notify player that their order was outbid
 *         notifyPlayer("Your order for " + event.getOrder().getName() + " was outbid!");
 *     }
 * }
 * }
 * </pre>
 * 
 * @see Order
 */
//TODO actually use this maybe? not sure what my thinking on this was back then
public class OutbidOrderEvent implements ICancellable {
    /**
     * The bazaar order that was affected.
     */
    @Getter
    private final Order order;
    
    /**
     * Whether the order was outbid (true) or became competitive again (false).
     */
    @Getter
    private final boolean isOutbid;
    
    /**
     * Creates a new OutbidOrderEvent.
     *
     * @param order the bazaar order that was affected
     * @param isOutbid true if the order was outbid, false if it became competitive again
     */
    public OutbidOrderEvent(Order order, boolean isOutbid) {
        this.order = order;
        this.isOutbid = isOutbid;
    }
    
    @Override
    public void setCancelled(boolean b) {

    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
