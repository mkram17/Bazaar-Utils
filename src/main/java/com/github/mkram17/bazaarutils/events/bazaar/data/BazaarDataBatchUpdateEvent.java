package com.github.mkram17.bazaarutils.events.bazaar.data;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.util.Set;

/**
 * Fired once per API snapshot after all products have been processed, carrying only the product
 * IDs whose book actually changed. Prefer this over {@link BazaarDataUpdateEvent} when a
 * subscriber needs to coalesce position checks across an entire snapshot tick rather than
 * reacting to each product mutation individually.
 *
 * <p>Use {@link #affects(String)} before doing per-product work.
 */
public class BazaarDataBatchUpdateEvent extends SkyBlockEvent {
    @Getter
    @NotNull
    private final @Unmodifiable Set<String> changedProductIds;

    @Getter
    @NotNull
    private final BazaarDataOrigin origin;

    public BazaarDataBatchUpdateEvent(@NotNull Set<String> changedProductIds, @NotNull BazaarDataOrigin origin) {
        this.changedProductIds = changedProductIds;
        this.origin = origin;
    }

    /** Convenience — returns true if this product was affected. */
    public boolean affects(String productId) {
        return changedProductIds.contains(productId);
    }
}
