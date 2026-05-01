package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Fired by {@link UserOrderHandler} when an active order's competitive standing transitions.
 *
 * <p>Only fired on actual transitions — identical consecutive standings produce no event.
 * {@link #getPrevious()} is {@code null} on the first observation of an order (no prior
 * reading exists yet). Only {@link OrderStatus.Set} and {@link OrderStatus.Partial} orders
 * are tracked; orders in other states cannot be outbid.
 *
 * @see UserOrderHandler
 */
@Getter
public class UserOrderPositionEvent extends SkyBlockEvent {
    /** The order this transition concerns. */
    private final Order order;

    /** The order's standing as of this observation. */
    private final Order.PositionContext current;
    /** The order's standing as of the previous observation, or {@code null} if this is the first. */
    private final Order.PositionContext previous;

    /** Which profile this order belongs to. */
    @NotNull
    private final ProfileKey profileKey;

    public UserOrderPositionEvent(
            @NotNull ProfileKey profileKey,
            @NotNull Order order,
            @NotNull Order.PositionContext current,
            @Nullable Order.PositionContext previous) {
        this.profileKey = profileKey;
        this.order = order;
        this.current = current;
        this.previous = previous;
    }
}