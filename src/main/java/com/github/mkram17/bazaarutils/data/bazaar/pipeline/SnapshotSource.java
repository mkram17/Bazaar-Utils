package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;

import java.util.List;

/**
 * Base for sources that splice a market read into the book — the one thing genuinely
 * identical between an API poll and a page render. What happens after (how many
 * products a tick covers, what eligibility rule narrows which orders
 * {@link FillInference#infer} considers, one write per product vs. one per profile
 * for the whole tick) differs enough between the two that each source composes
 * {@link #splice} with {@link FillInference}'s own methods directly, in its own
 * control flow, rather than through a shared method carrying a parameter only one
 * caller ever uses.
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