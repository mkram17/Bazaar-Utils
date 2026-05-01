package com.github.mkram17.bazaarutils.events.bazaar.data;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.util.Set;

/**
 * Fired once after a full API snapshot is processed, carrying only the product
 * IDs whose price pool actually changed. Subscribers should check
 * {@code changedProductIds()} before doing any work.
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
