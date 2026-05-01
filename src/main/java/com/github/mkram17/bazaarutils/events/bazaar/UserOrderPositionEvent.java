package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * <p>Only fired on actual transitions — if the underlying market standing hasn't changed since
 * the last book update for this product, no event is posted. {@link #getPreviousPosition()} is
 * {@code null} the first time an order is checked (typically right after it's placed) — that's
 * the absence of a prior reading, not a transition in the usual sense.
 *
 * <p>Only {@link OrderStatus.Set} and {@link OrderStatus.Partial} orders are tracked — terminal
 * states cannot be outbid.
 */
public class UserOrderPositionEvent extends SkyBlockEvent {
    @Getter
    private final Order order;

    @Getter
    private final Order.PositionContext position;

    @Getter
    private final Order.PositionContext previousPosition;

    public UserOrderPositionEvent(Order order, Order.PositionContext previousPosition, Order.PositionContext position) {
        this.order = order;
        this.position = position;
        this.previousPosition = previousPosition;
    }
}