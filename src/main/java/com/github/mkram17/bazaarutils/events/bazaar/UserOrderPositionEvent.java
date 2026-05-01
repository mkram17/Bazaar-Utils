package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.events.ProfileScopedEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Fired by {@link UserOrderHandler} when an active order's raw {@link PricingPosition.PositionContext}
 * changes (e.g. number of levels ahead or total/own order counts at top-of-book).
 *
 * <p>Because {@code PositionContext} is deliberately policy-free, consecutive events may
 * classify to the same {@link PricingPosition} under a given consumer's {@code selfOutbid}
 * policy (for example, {@code levelsAhead} changing from 2 to 3 is still {@code OUTBID}).
 * Consumers that only care about discrete standing transitions should deduplicate on
 * {@code current.classify(selfOutbid)} vs. {@code previous.classify(selfOutbid)}.
 *
 * <p>{@link #getPrevious()} is {@code null} on the first observation of an order (no prior
 * reading exists yet). Only {@link OrderStatus.Set} and {@link OrderStatus.Partial} orders
 * are tracked; orders in other states cannot be outbid.
 *
 * @see UserOrderHandler
 */
@Getter
public class UserOrderPositionEvent extends SkyBlockEvent implements ProfileScopedEvent {
    /** The order this transition concerns. */
    @NotNull
    private final Order order;

    /** The order's standing as of this observation. */
    @NotNull
    private final PricingPosition.PositionContext current;

    /** The order's standing as of the previous observation, or {@code null} if this is the first. */
    @Nullable
    private final PricingPosition.PositionContext previous;

    /** Which profile this order belongs to. */
    @NotNull
    private final ProfileKey profileKey;

    public UserOrderPositionEvent(
            @NotNull ProfileKey profileKey,
            @NotNull Order order,
            @NotNull PricingPosition.PositionContext current,
            @Nullable PricingPosition.PositionContext previous) {
        this.profileKey = profileKey;
        this.order = order;
        this.current = current;
        this.previous = previous;
    }
}
