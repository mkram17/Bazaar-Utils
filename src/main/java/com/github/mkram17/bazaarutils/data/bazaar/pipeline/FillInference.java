package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FillInference {
    private static final BazaarLogger LOG = BazaarLogger.of(FillInference.class);

    private FillInference() {}

    public record Result(Order order, int delta) {
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
     * Fill inference for any snapshot source.
     *
     * <p>Groups active orders by (side, price). FIFO is skipped whenever external
     * orders share the price level. The {@code level == null} branch differs by origin:
     *
     * <ul>
     *   <li>{@link BazaarDataOrigin.PageSummary} — absent ≠ vanished; rate-throttling
     *       can drop levels silently, so we skip rather than infer a full fill.</li>
     *   <li>{@link BazaarDataOrigin.ApiSnapshot} — absence within the coverage window
     *       is authoritative; mark all orders at that price fully consumed.</li>
     * </ul>
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
                        sorted.forEach(order -> PlayerLogger.debug("%s — Fill advanced %d → %d (Δ%d, level vanished): %s".formatted(
                                origin.describe(), order.filledAmount(), order.originalAmount(),
                                order.unfilledAmount(), order.describe()), notifType, LOG));

                        return distributeFIFO(sorted, totalRemaining).stream();
                    }

                    if (level.orderCount() != group.size()) return Stream.empty();

                    int totalExpected = sorted.stream().mapToInt(Order::unfilledAmount).sum();
                    int totalDelta    = totalExpected - (int) level.totalVolume();

                    if (totalDelta <= 0) return Stream.empty();

                    if (group.size() == 1) {
                        var order = sorted.getFirst();
                        int inferredFilled = order.originalAmount() - (int) level.totalVolume();
                        PlayerLogger.debug("%s — Fill advanced %d → %d (Δ%d, sole order): %s".formatted(
                                origin.describe(), order.filledAmount(), inferredFilled,
                                inferredFilled - order.filledAmount(), order.describe()), notifType, LOG);
                    } else {
                        PlayerLogger.debug("%s — Fill inference (FIFO, %d orders @ %.4f, Δ%d)".formatted(
                                origin.describe(), group.size(), price, totalDelta), notifType, LOG);
                    }

                    return distributeFIFO(sorted, totalDelta).stream();
                })
                .toList();
    }

    /**
     * Writes a batch of inferred fills to storage, reindexes, fires fill events, and
     * persists. Returns {@code true} if storage changed.
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

        LOG.debug("Applying Δ{} inferred fills", fillMap.size());

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

                    PlayerLogger.debug(msg, NotificationType.ORDER_LIFECYCLE, LOG);
                });

        return true;
    }
}