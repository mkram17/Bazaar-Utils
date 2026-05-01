package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;

import java.util.List;
import java.util.function.Function;

/**
 * Base for sources that splice a market read into the book and infer fill progress
 * across every known position from the result.
 *
 * <p>{@link #splice} and {@link #inferForPosition} are the two things genuinely
 * identical between an API poll and a page render. {@link #processProduct} composes
 * both into the full per-product loop — the right shape for a source that only ever
 * handles one product per invocation ({@code PageSummaryDataSource}: nothing to
 * batch, so nothing lost using it directly).
 *
 * <p>{@code ApiSnapshotDataSource} deliberately does NOT call {@link #processProduct}
 * for its own outer loop — see its own class doc for why it composes {@link #splice}
 * and {@link #inferForPosition} into its own multi-product batching instead.
 */
public abstract class SnapshotSource extends BUListener {

    /** Splices both sides of a read via {@link BookMutation.Splice}. */
    protected final boolean splice(String productId, List<PriceLevel> askLevels, List<PriceLevel> bidLevels, BazaarDataOrigin.Snapshot origin) {
        var mutation = BookMutation.splice(PriceType.INSTABUY, askLevels).then(BookMutation.splice(PriceType.INSTASELL, bidLevels));

        return mutation.apply(productId, origin);
    }

    /**
     * Infers fill progress for {@code productId} against ONE known position's active
     * orders, after {@code eligibilityFilter} narrows them further — identity for a
     * source with no extra eligibility rule; a real filter for one that has one (an
     * API poll's session-boundary check, most directly).
     */
    protected final List<OrderDelta.Update<BazaarDataOrigin>> inferForPosition(
            String productId, ProductData data, List<Order> positionOrders,
            Function<List<Order>, List<Order>> eligibilityFilter,
            BazaarDataOrigin.Snapshot origin, NotificationType notifType) {
        var eligible = eligibilityFilter.apply(positionOrders.stream().filter(Order::isActive).toList());

        return FillInference.infer(productId, data, eligible, origin, notifType);
    }

    /** Full per-product flow: splice, then infer + applyAll for every known position, one write per position. */
    protected final boolean processProduct(
            String productId, ProductData data, List<PriceLevel> askLevels, List<PriceLevel> bidLevels,
            Function<List<Order>, List<Order>> eligibilityFilter,
            BazaarDataOrigin.Snapshot origin, NotificationType notifType) {
        boolean bookChanged = splice(productId, askLevels, bidLevels, origin);

        boolean anyInferred = false;

        for (var entry : UserOrdersStorage.allKnown().entrySet()) {
            var inferences = inferForPosition(productId, data, entry.getValue(), eligibilityFilter, origin, notifType);

            if (FillInference.applyAll(entry.getKey(), inferences, origin)) anyInferred = true;
        }

        return bookChanged || anyInferred;
    }
}