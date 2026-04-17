package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
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
    public final @Unmodifiable Set<String> changedProductIds;

    @Getter
    @NotNull
    public final DataSources source;

    public BazaarDataBatchUpdateEvent(@NotNull Set<String> changedProductIds, @NotNull DataSources source) {
        this.changedProductIds = changedProductIds;
        this.source = source;
    }

    /** Convenience — returns true if this product was affected. */
    public boolean affects(String productId) {
        return changedProductIds.contains(productId);
    }
}
