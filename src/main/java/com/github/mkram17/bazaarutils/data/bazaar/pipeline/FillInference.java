package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.LevelReconciliation;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deduction of fill progress for tracked orders from how a product's book moved, and —
 * via {@link #settle} — the mechanism by which that deduction reaches every known
 * position this mod tracks, not only the profile whose own action triggered it.
 *
 * <p>{@link #infer} is the snapshot-scoped form, taking an already-resolved
 * {@link ProductData} and order list from its caller rather than looking either up
 * itself.
 *
 * <p>{@link #settle} wraps {@link UserOrdersStorage#apply} and, immediately after that
 * write lands, reconciles every known position — across every known profile, not just
 * the one written to — on the touched product(s).
 *
 * <p>{@link #applyAll} reuses {@link OrderDelta.Update.UpdateKind#FILL}'s event
 * selection rather than reimplementing it.
 */
public final class FillInference {
    private FillInference() {}

    private static List<OrderDelta.Update<BazaarDataOrigin>> distributeFIFO(List<Order> sortedByQueue, int totalDelta, BazaarDataOrigin origin) {
        var results = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();
        int remaining = totalDelta;

        for (Order order : sortedByQueue) {
            if (remaining <= 0) break;
            int canFill = order.originalAmount() - order.filledAmount();

            int delta = Math.min(remaining, canFill);
            if (delta > 0) {
                var after = order.withFill(delta, origin);

                results.add(OrderDelta.Update.fill(order, after, BookMutation.none()));
            }

            remaining -= delta;
        }

        return results;
    }

    /**
     * Infers the fill delta for one (side, price) group of same-priced tracked orders,
     * or returns no inferences when the evidence doesn't support one.
     *
     * <p>Requires exclusive occupancy to infer anything when the level still exists:
     * {@code level.orderCount()} must equal {@code group.size()}, since a level shared
     * with an untracked order gives no way to tell which order's volume actually moved.
     * When the level has vanished entirely, {@code vanishIsAuthoritative} alone decides
     * whether that absence is trusted to mean every order in the group filled its
     * remainder — no occupancy check applies there, since there's no level left to
     * compare counts against.
     */
    private static List<OrderDelta.Update<BazaarDataOrigin>> inferGroup(
            TransactionType.Side side, double price, List<Order> group, ProductData data,
            boolean vanishIsAuthoritative, BazaarDataOrigin origin, NotificationType notifType) {
        var level = data.entryAt(TransactionType.of(side, TransactionType.Method.ORDER), price)
                .flatMap(LevelReconciliation::tradable).orElse(null);
        var sorted = group.stream().sorted(Order.byFillPriority(side)).toList();

        if (level == null) {
            if (!vanishIsAuthoritative) return List.of();

            int totalRemaining = sorted.stream().mapToInt(Order::unfilledAmount).sum();

            sorted.forEach(order -> PlayerActionUtil.notifyAll("%s — Fill advanced %d → %d (Δ%d, level vanished): %s".formatted(origin.describe(), order.filledAmount(), order.originalAmount(), order.unfilledAmount(), order.describe()), notifType));

            return distributeFIFO(sorted, totalRemaining, origin);
        }

        if (level.orderCount() != group.size()) return List.of();

        int totalExpected = sorted.stream().mapToInt(Order::unfilledAmount).sum();

        int totalDelta = totalExpected - (int) level.totalVolume();
        if (totalDelta <= 0) return List.of();

        if (group.size() == 1) {
            var order = sorted.getFirst();
            int inferredFilled = order.originalAmount() - (int) level.totalVolume();

            PlayerActionUtil.notifyAll("%s — Fill advanced %d → %d (Δ%d, sole order): %s".formatted(origin.describe(), order.filledAmount(), inferredFilled, inferredFilled - order.filledAmount(), order.describe()), notifType);
        } else {
            PlayerActionUtil.notifyAll("%s — Fill inference (FIFO, %d orders @ %.4f, Δ%d)".formatted(origin.describe(), group.size(), price, totalDelta), notifType);
        }

        return distributeFIFO(sorted, totalDelta, origin);
    }

    /**
     * Derives fill inferences for {@code trackedOrders} against {@code data}'s
     * already-applied book state. {@code vanishIsAuthoritative} is set from whether
     * {@code origin} is an {@link BazaarDataOrigin.ApiSnapshot} — a full-depth read can
     * treat a vanished level as genuinely gone; a partial one
     * ({@link BazaarDataOrigin.PageSummary}) cannot.
     */
    public static List<OrderDelta.Update<BazaarDataOrigin>> infer(
            String productId, ProductData data, List<Order> trackedOrders,
            BazaarDataOrigin.Snapshot origin, NotificationType notifType) {
        boolean vanishIsAuthoritative = origin instanceof BazaarDataOrigin.ApiSnapshot;

        return trackedOrders.stream()
                .filter(order -> order.productId().equals(productId))
                .collect(Collectors.groupingBy(order -> Map.entry(order.side(), order.pricePerItem())))
                .entrySet().stream()
                .flatMap(e -> inferGroup(e.getKey().getKey(), e.getKey().getValue(), e.getValue(), data, vanishIsAuthoritative, origin, notifType).stream())
                .toList();
    }

    /** Persists already-computed inferences for {@code key} in one write, firing {@link OrderDelta.Update.UpdateKind#FILL}'s event per affected order. */
    public static boolean applyAll(ProfileKey key, List<OrderDelta.Update<BazaarDataOrigin>> inferences, BazaarDataOrigin origin) {
        if (inferences.isEmpty()) return false;

        var beforeByOrderId = inferences.stream().collect(Collectors.toMap(it -> it.before().id(), OrderDelta.Update::before));

        var fillMap = inferences.stream().collect(Collectors.toMap(it -> it.before().id(), OrderDelta.Update::after));
        UserOrdersStorage.StorageOp operation = current -> current.stream()
                .map(order -> fillMap.getOrDefault(order.id(), order)).collect(Collectors.toCollection(ArrayList::new));

        Util.logMessage("Applying Δ%d inferred fills [%s]".formatted(fillMap.size(), key));

        var result = UserOrdersStorage.apply(key, operation.then(UserOrdersStorage.StorageOp.reindex()));

        result.stream().filter(order -> fillMap.containsKey(order.id())).forEach(after -> {
            var before = beforeByOrderId.get(after.id());

            OrderDelta.Update.UpdateKind.FILL.getEvent(before, after, key).post(BazaarUtils.EVENT_BUS);

            String msg = after.status() instanceof OrderStatus.Filled
                    ? "%s — Fill advanced → fully filled (inferred): %s".formatted(origin.describe(), after.describe())
                    : "%s — Fill advanced (inferred): %s".formatted(origin.describe(), after.describe());

            PlayerActionUtil.notifyAll(msg, NotificationType.ORDERDATA);
        });

        return true;
    }

    /**
     * Checks every known profile's active orders for {@code productId} against
     * {@code data}'s current book and persists whatever fill inferences result, one
     * profile at a time.
     *
     * <p>Vanish is always treated as authoritative here, regardless of what
     * {@link #infer}'s own conditional would say: this runs only from a confirmed
     * {@link BazaarDataOrigin.UserPositionEvent}, so the book was just mutated by an
     * action known to have happened, and a level it leaves empty is genuinely gone —
     * not merely unreported by a partial read.
     */
    private static void reconcileKnownPositions(String productId, ProductData data, BazaarDataOrigin.UserPositionEvent origin, NotificationType notifType) {
        UserOrdersStorage.allKnown().forEach((key, storage) -> {
            var activeOrders = storage.stream().filter(Order::isActive).toList();

            var inferences = activeOrders.stream()
                    .filter(order -> order.productId().equals(productId))
                    .collect(Collectors.groupingBy(order -> Map.entry(order.side(), order.pricePerItem())))
                    .entrySet().stream()
                    .flatMap(entry -> inferGroup(entry.getKey().getKey(), entry.getKey().getValue(), entry.getValue(), data, true, origin, notifType).stream())
                    .toList();

            applyAll(key, inferences, origin);
        });
    }

    /**
     * Reconciles every known position on {@code productId} against its current book,
     * if the product has ever been registered. No-op otherwise.
     */
    public static void settle(@NotNull String productId, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var data = BazaarDataRegistry.get(productId);

        if (data != null) reconcileKnownPositions(productId, data, origin, NotificationType.ORDERDATA);
    }

    /**
     * Applies {@code operation} to {@code key}'s storage, then reconciles every known
     * position — across every known profile, not only {@code key} — on each of
     * {@code touchedProducts}. Returns the post-persist list for {@code key}.
     */
    public static @NotNull List<Order> settle(@NotNull ProfileKey key, @NotNull Set<String> touchedProducts, @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation) {
        var result = UserOrdersStorage.apply(key, operation);

        for (String productId : touchedProducts) settle(productId, origin);

        return result;
    }

    /** Single-product convenience for {@link #settle(ProfileKey, Set, BazaarDataOrigin.UserPositionEvent, UserOrdersStorage.StorageOp)}. */
    public static @NotNull List<Order> settle(@NotNull ProfileKey key, @NotNull String productId, @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation) {
        return settle(key, Set.of(productId), origin, operation);
    }
}