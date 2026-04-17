package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Fired by {@code BazaarProductRegistry} whenever a product's price pool is
 * mutated — after any splice, placement, or volume decrement that could shift
 * the best available price for a product.
 *
 * <p>Subscribers that care about derived state (e.g. order position relative
 * to market) should react to this event and recompute from the accessors
 * rather than receiving a pre-computed conclusion.
 */
public class BazaarDataUpdateEvent extends SkyBlockEvent {
    @Getter
    @NotNull
    private final String productId;

    @Getter
    @NotNull
    private final DataSources source;

    public BazaarDataUpdateEvent(@NotNull String productId, @NotNull DataSources source) {
        this.productId = productId;
        this.source = source;
    }
}