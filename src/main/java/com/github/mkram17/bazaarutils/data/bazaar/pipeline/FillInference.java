package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.sources.gui.PageSummaryDataSource;
import com.github.mkram17.bazaarutils.data.bazaar.sources.remote.ApiSnapshotDataSource;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stateless utility for inferring fill advances from market book snapshots.
 *
 * <p>Inference is predicated on <b>exclusive occupancy</b>: when the book's
 * {@link com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel#orderCount()} at a
 * (side, price) level exactly equals the number of tracked active orders at that level, all
 * volume changes must have come from those orders and can be distributed FIFO. When external
 * orders share the level the inference is skipped — there is no way to determine which orders
 * consumed the volume.
 *
 * <p>Two evidence types trigger inference:
 * <ul>
 *   <li><b>Level vanished</b> — authoritative only for
 *       {@link BazaarDataOrigin.ApiSnapshot}. All remaining unfilled volume at the level is
 *       consumed FIFO.</li>
 *   <li><b>Volume delta</b> — the level exists but its {@code totalVolume} fell below the
 *       sum of tracked unfilled amounts. The shortfall is distributed FIFO.</li>
 * </ul>
 */
public final class FillInference {
    private FillInference() {}

    /** An inferred fill delta for one order. */
    public record Result(Order order, int delta) {
        /** Returns the order advanced by {@link #delta} fills. */
        public Order applied(BazaarDataOrigin origin) {
            return order.withFill(delta, origin);
        }
    }

    /**
     * Distributes {@code totalDelta} fill units across {@code sortedByQueue} in FIFO
     * order, capped per order by remaining unfilled capacity. Stops when the delta is
     * exhausted.
     */
    private static List<Result> distributeFIFO(List<Order> sortedByQueue, int totalDelta) {
        var results = new ArrayList<Result>();
        int remaining = totalDelta;

        for (Order order : sortedByQueue) {
            if (remaining <= 0) break;
            int canFill = order.originalAmount() - order.filledAmount();

            int delta = Math.min(remaining, canFill);
            if (delta > 0) results.add(new Result(order, delta));

            remaining -= delta;
        }

        return results;
    }

    /**
     * Derives fill advances for the tracked active orders of {@code productId} by comparing their
     * stored unfilled amounts against the post-apply book in {@code data}.
     *
     * <p>Inference requires <b>exclusive occupancy</b>: the book level's
     * {@link com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel#orderCount()} must equal
     * the number of tracked active orders at that (side, price) pair. When external orders share
     * the level the source of any volume change is ambiguous and the group is skipped.
     *
     * <p>When a level has vanished entirely, the vanish is treated as authoritative evidence of
     * consumption only for {@link BazaarDataOrigin.ApiSnapshot} — a
     * {@link BazaarDataOrigin.PageSummary} can silently drop levels due to rate-throttling and
     * does not warrant inferring a full fill.
     *
     * @return raw inferences; caller must pass the result to {@link #applyAll} to persist and
     *         fire events.
     */
    public static List<Result> infer(
            String productId,
            ProductData data,
            List<Order> activeOrders,
            BazaarDataOrigin.Snapshot origin,
            NotificationType notifType) {

        boolean vanishIsAuthoritative = origin instanceof BazaarDataOrigin.ApiSnapshot;

        return activeOrders.stream()
                .filter(order -> order.productId().equals(productId))
                .collect(Collectors.groupingBy(order -> Map.entry(order.side(), order.pricePerItem())))
                .entrySet().stream()
                .flatMap(entry -> {
                    var side = entry.getKey().getKey();
                    var price = entry.getKey().getValue();
                    var group = entry.getValue();
                    var level = data.bookFor(TransactionType.of(side, TransactionType.Method.ORDER)).get(price);
                    var sorted = group.stream().sorted(Order.byFillPriority(side)).toList();

                    if (level == null) {
                        if (!vanishIsAuthoritative) return Stream.empty();

                        int totalRemaining = sorted.stream().mapToInt(Order::unfilledAmount).sum();
                        sorted.forEach(order -> PlayerActionUtil.notifyAll("%s — Fill advanced %d → %d (Δ%d, level vanished): %s".formatted(
                                origin.describe(), order.filledAmount(), order.originalAmount(),
                                order.unfilledAmount(), order.describe()), notifType));

                        return distributeFIFO(sorted, totalRemaining).stream();
                    }

                    if (level.orderCount() != group.size()) return Stream.empty();

                    int totalExpected = sorted.stream().mapToInt(Order::unfilledAmount).sum();
                    int totalDelta    = totalExpected - (int) level.totalVolume();

                    if (totalDelta <= 0) return Stream.empty();

                    if (group.size() == 1) {
                        var order = sorted.getFirst();
                        int inferredFilled = order.originalAmount() - (int) level.totalVolume();
                        PlayerActionUtil.notifyAll("%s — Fill advanced %d → %d (Δ%d, sole order): %s".formatted(
                                origin.describe(), order.filledAmount(), inferredFilled,
                                inferredFilled - order.filledAmount(), order.describe()), notifType);
                    } else {
                        PlayerActionUtil.notifyAll("%s — Fill inference (FIFO, %d orders @ %.4f, Δ%d)".formatted(
                                origin.describe(), group.size(), price, totalDelta), notifType);
                    }

                    return distributeFIFO(sorted, totalDelta).stream();
                })
                .toList();
    }

    /**
     * Applies all inferred fill deltas to storage in one atomic write, fires
     * {@link com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent.Filled} or
     * {@link com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent.PartiallyFilled}
     * per affected order, and returns {@code true} if storage changed. No-ops when
     * {@code inferences} is empty or storage is not loaded.
     */
    public static boolean applyAll(List<Result> inferences, BazaarDataOrigin origin) {
        if (inferences.isEmpty()) return false;
        if (!UserOrdersStorage.isLoaded()) return false;

        var deltaByOrderId = inferences.stream()
                .collect(Collectors.toMap(it -> it.order().id(), Result::delta));

        var fillMap = inferences.stream()
                .collect(Collectors.toMap(it -> it.order().id(), it -> it.applied(origin)));

        UserOrdersStorage.StorageOp operation = current -> current.stream()
                .map(order -> fillMap.getOrDefault(order.id(), order))
                .collect(Collectors.toCollection(ArrayList::new));

        Util.logMessage("Applying Δ%d inferred fills".formatted(fillMap.size()));

        var result = UserOrdersStorage.apply(operation.then(UserOrdersStorage.StorageOp.reindex()));

        result.stream()
                .filter(order -> fillMap.containsKey(order.id()))
                .forEach(order -> {
                    UserOrderEvent event;
                    String msg;

                    if (order.status() instanceof OrderStatus.Filled) {
                        event = new UserOrderEvent.Filled(order);
                        msg = "%s — Fill advanced → fully filled (inferred): %s".formatted(origin.describe(), order.describe());
                    } else {
                        int filledDelta = deltaByOrderId.get(order.id());

                        event = new UserOrderEvent.PartiallyFilled(order, filledDelta);
                        msg = "%s — Fill advanced (inferred): %s".formatted(origin.describe(), order.describe());
                    }

                    event.post(BazaarUtils.EVENT_BUS);

                    PlayerActionUtil.notifyAll(msg, NotificationType.ORDERDATA);
                });

        return true;
    }
}