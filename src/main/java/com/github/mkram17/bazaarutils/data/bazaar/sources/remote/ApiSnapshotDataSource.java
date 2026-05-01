package com.github.mkram17.bazaarutils.data.bazaar.sources.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.SnapshotSource;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataBatchUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.*;

/**
 * Applies a full Hypixel API snapshot across every product it covers, then settles
 * fill inference for every known profile against the whole tick in one pass.
 *
 * <p>Splices every product once, with no profile dimension, via {@link #splice} —
 * collecting each product's {@link BookMutation.MutationOutcome} rather than acting
 * on it immediately. Once the whole snapshot has been spliced, every outcome is
 * handed to {@link FillInference#settle} together, which does its own cross-profile
 * grouping and writes exactly once per affected profile — this class never iterates
 * profiles itself.
 *
 * <p>Settlement is gated by {@link #sessionEligibility}, the session-boundary trust
 * rule shared by every snapshot source.
 */
@DataSource
public final class ApiSnapshotDataSource extends SnapshotSource {
    public ApiSnapshotDataSource() {}

    /** One product's splice outcome for this tick, collected before being handed to {@link FillInference#settle} together. */
    private record SplicedProduct(String productId, BookMutation.MutationOutcome outcome) {}

    @Subscription(priority = Priority.FIRST)
    public void onApiSnapshot(ApiSnapshotEvent event) {
        var origin = new BazaarDataOrigin.ApiSnapshot(event.getTimestamp());

        var spliced = new ArrayList<SplicedProduct>(event.getSnapshot().size());

        for (var entry : event.getSnapshot().entrySet()) {
            String productId = entry.getKey();

            var outcome = splice(
                    productId,
                    entry.getValue().asksLevels(),
                    entry.getValue().bidsLevels(),
                    origin
            );

            spliced.add(new SplicedProduct(productId, outcome));
        }

        var changed = new HashSet<String>();
        for (var product : spliced) {
            if (product.outcome().changed()) {
                changed.add(product.productId());
            }
        }
        var outcomes = spliced.stream().map(SplicedProduct::outcome).toList();

        changed.addAll(FillInference.settle(outcomes, origin, sessionEligibility()).changedProducts());

        if (!changed.isEmpty()) {
            new BazaarDataBatchUpdateEvent(Collections.unmodifiableSet(changed), origin).post(BazaarUtils.EVENT_BUS);

            PlayerActionUtil.notifyAll(
                    "%s — %d products changed".formatted(origin.describe(), changed.size()),
                    NotificationType.BAZAARDATA
            );
        }
    }

    private static boolean eligibleOrder(
            Order order,
            BookMutation.MutationOutcome outcome,
            long sessionStart
    ) {
        if (!order.priceIsExact()) return false;

        var type = TransactionType.of(order.side(), TransactionType.Method.ORDER).getPriceType();
        double price = order.pricePerItem();

        if (!outcome.canEvict(type, price)) return false;          // snapshot must cover this price
        if (order.lastUpdatedAt() >= sessionStart) return true;    // tracked this session: baseline trusted

        // resumed from disk: baseline untrusted, so only a level that's fully gone counts
        return outcome.observe(type, price) instanceof BookMutation.MutationOutcome.LevelObservation.ProvenAbsent;
    }
}