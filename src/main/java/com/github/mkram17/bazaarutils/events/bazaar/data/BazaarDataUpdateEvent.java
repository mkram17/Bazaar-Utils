package com.github.mkram17.bazaarutils.events.bazaar.data;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Fired after any mutation to a single product's order book — a snapshot splice, optimistic
 * placement, fill decrement, or cancel decrement.
 *
 * <p>Subscribers that maintain derived state (order competitive position, price suggestions)
 * should recompute from the accessors in
 * {@link com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo} in response to
 * this event rather than caching pre-computed conclusions across book updates.
 */
public class BazaarDataUpdateEvent extends SkyBlockEvent {
    @Getter
    @NotNull
    private final String productId;

    @Getter
    @NotNull
    private final BazaarDataOrigin origin;

    public BazaarDataUpdateEvent(@NotNull String productId, @NotNull BazaarDataOrigin origin) {
        this.productId = productId;
        this.origin = origin;
    }
}