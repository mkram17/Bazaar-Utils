package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Fired by {@link UserOrderHandler} when an active order's competitive standing transitions.
 *
 * <p>Only fired on actual transitions — identical consecutive standings produce no event.
 * {@link #getPreviousPosition()} is {@code null} on the first observation of an order
 * (no prior reading exists yet). Only {@link OrderStatus.Set} and {@link OrderStatus.Partial}
 * orders are tracked; orders in other states cannot be outbid.
 */
public class UserOrderPositionEvent extends SkyBlockEvent {
    @Getter
    private final Order order;

    @Getter
    private final Order.PositionContext position;

    @Getter
    private final Order.PositionContext previousPosition;

    public UserOrderPositionEvent(Order order, Order.PositionContext position, Order.PositionContext previousPosition) {
        this.order = order;
        this.position = position;
        this.previousPosition = previousPosition;
    }
}