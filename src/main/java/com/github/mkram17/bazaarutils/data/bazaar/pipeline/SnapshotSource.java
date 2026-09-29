package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;

import java.util.List;

/**
 * Base for sources that splice a market read into the book. Fill inference is not
 * this class's concern — each subclass hands its own accumulated
 * {@link BookMutation.MutationOutcome}s to {@link FillInference#settle} directly, so
 * this only ever needs to give both sides' splice result back to the caller.
 */
public abstract class SnapshotSource extends BUListener {
    /** Splices both sides of a read via {@link BookMutation.Splice}, returning what changed and what the read now authorizes. */
    protected final <O extends BazaarDataOrigin.Snapshot> BookMutation.MutationOutcome splice(
            String productId,
            List<PriceLevel<O>> askLevels,
            List<PriceLevel<O>> bidLevels,
            O origin) {
        return BookMutation.splice(PriceType.INSTABUY, askLevels)
                .then(BookMutation.splice(PriceType.INSTASELL, bidLevels))
                .apply(productId, origin);
    }
}