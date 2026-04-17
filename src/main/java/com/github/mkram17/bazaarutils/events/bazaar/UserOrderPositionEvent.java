package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Fired by {@link com.github.mkram17.bazaarutils.data.bazaar.sources.OrderPositionTracker}
 * when a live order's {@link PricingPosition} changes relative to the current book.
 *
 * <p>Only fired on actual transitions — if position is unchanged since the last
 * book update for this product, no event is posted.
 *
 * <p>Only {@link com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus.Set}
 * and {@link com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus.Partial}
 * orders are tracked — terminal states cannot be outbid.
 *
 * <p>Consumers discriminate on {@link #getPosition()} directly:
 * <pre>{@code
 * public void onPositionChange(OrderPositionEvent event) {
 *     switch (event.getPosition()) {
 *         case OUTBID -> notifyOutbid(event.getOrder());
 *         case COMPETITIVE -> notifyCompetitive(event.getOrder());
 *         case MATCHED -> notifyMatched(event.getOrder());
 *     }
 * }
 * }</pre>
 */
public class UserOrderPositionEvent extends SkyBlockEvent {

    @Getter private final Order order;
    @Getter private final PricingPosition position;
    @Getter private final PricingPosition previousPosition;

    public UserOrderPositionEvent(Order order, PricingPosition previousPosition, PricingPosition position) {
        this.order = order;
        this.previousPosition = previousPosition;
        this.position = position;
    }
}