package com.github.mkram17.bazaarutils.data.bazaar.sources.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.SnapshotSource;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
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
import java.util.function.Function;

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
 * <p>{@link #eligibleOrder} — the session-boundary trust rule — is specific to this
 * source: an order resumed from a prior session has no baseline this source can
 * trust unless its price has already vanished from the book entirely.
 */
@DataSource
public final class ApiSnapshotDataSource extends SnapshotSource {
    public ApiSnapshotDataSource() {}

    /** One product's splice outcome for this tick, collected before being handed to {@link FillInference#settle} together. */
    private record SplicedProduct(String productId, BookMutation.MutationOutcome outcome) {}

    @Subscription(priority = Priority.FIRST)
    public void onApiSnapshot(ApiSnapshotEvent event) {
        var origin = new BazaarDataOrigin.ApiSnapshot(event.getTimestamp());
        var sessionStart = TimeUtil.getModInitTime().toInstant().toEpochMilli();

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

        FillInference.Eligibility eligible =
                (order, mutation) -> eligibleOrder(order, mutation, sessionStart);

        var outcomes = spliced.stream().map(SplicedProduct::outcome).toList();

        changed.addAll(FillInference.settle(outcomes, origin, eligible).changedProducts());

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
        ProductData data = outcome.book();
        if (data == null) return false;

        if (!order.productId().equals(data.getProductId())) return false;

        var transaction = TransactionType.of(
                order.side(),
                TransactionType.Method.ORDER
        );

        boolean baselineTrusted =
                order.placedAt() >= sessionStart
                        || order.lastUpdatedAt() >= sessionStart
                        || data.tradableLevels(transaction).get(order.pricePerItem()) == null;

        if (!baselineTrusted) return false;

        return outcome.canEvict(
                transaction.getPriceType(),
                order.pricePerItem()
        );
    }
}