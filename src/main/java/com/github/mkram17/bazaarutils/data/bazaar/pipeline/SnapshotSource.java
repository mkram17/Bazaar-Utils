package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;

import java.util.List;

/**
 * Base for sources that splice a market read into the book. Fill inference is not
 * this class's concern — each subclass hands its own accumulated
 * {@link BookMutation.MutationOutcome}s to {@link FillInference#settle} directly, so
 * this only gives both sides' splice result back to the caller, plus the one
 * {@link #sessionEligibility} rule every snapshot-backed settle shares.
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

    /** The session-boundary trust rule, as an {@link FillInference.Eligibility} fixed to this session's start. */
    protected static FillInference.Eligibility sessionEligibility() {
        long sessionStart = TimeUtil.getModInitTime().toInstant().toEpochMilli();

        return (order, outcome) -> eligibleOrder(order, outcome, sessionStart);
    }

    /**
     * Eligible when the read has standing over the order's price and the mod has
     * tracked the order continuously: touched this session, or its level proven gone
     * outright. The market moves while the mod is off, so a level resting at a resumed
     * order's price may not be the level the order sat in; the original may have
     * emptied and a new one been placed at the same price. Diffing against it would
     * credit that level's volume to this order. A vanished level has no such
     * ambiguity: nothing rests there, whatever happened in between.
     */
    private static boolean eligibleOrder(Order order, BookMutation.MutationOutcome outcome, long sessionStart) {
        var type = TransactionType.of(order.side(), TransactionType.Method.ORDER).getPriceType();
        double price = order.pricePerItem();

        if (!outcome.canEvict(type, price)) return false;
        if (order.placedAt() >= sessionStart || order.lastUpdatedAt() >= sessionStart) return true;

        return outcome.observe(type, price) instanceof BookMutation.MutationOutcome.LevelObservation.ProvenAbsent;
    }
}