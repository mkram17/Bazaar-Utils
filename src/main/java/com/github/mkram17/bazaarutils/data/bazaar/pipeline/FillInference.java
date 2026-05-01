package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.LevelReconciliation;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class FillInference {
    private FillInference() {}

    public static @NotNull Set<String> settle(
            @NotNull Collection<BookMutation.MutationOutcome> outcomes,
            @NotNull BazaarDataOrigin origin, @NotNull Eligibility eligibility) {
        return Settlement.settle(outcomes, origin, eligibility);
    }

    public static @NotNull Set<String> settle(@NotNull BookMutation.MutationOutcome outcome, @NotNull BazaarDataOrigin origin) {
        return Settlement.settle(List.of(outcome), origin, Eligibility.ALWAYS);
    }

    public static @NotNull List<Order> applyAndSettle(
            @NotNull ProfileKey key, @NotNull Collection<BookMutation.MutationOutcome> outcomes,
            @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation) {
        return Settlement.applyAndSettle(key, outcomes, origin, operation);
    }

    public static @NotNull List<Order> applyAndSettle(
            @NotNull ProfileKey key, @NotNull BookMutation.MutationOutcome outcome,
            @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation) {
        return applyAndSettle(key, List.of(outcome), origin, operation);
    }

    @FunctionalInterface
    public interface Eligibility {
        boolean accepts(@NotNull Order order, @NotNull BookMutation.MutationOutcome outcome);

        Eligibility ALWAYS = (_, _) -> true;
    }

    private record PriceGroup(@NotNull TransactionType.Side side, double price) {}

    private record Observation(
            @NotNull ProductData book,
            @NotNull BookMutation.MutationOutcome outcome,
            @NotNull BazaarDataOrigin origin) {
        @Nullable
        PriceLevel<?> levelAt(@NotNull PriceGroup group) {
            var transaction = TransactionType.of(group.side(), TransactionType.Method.ORDER);
            return book.entryAt(transaction, group.price()).flatMap(LevelReconciliation::tradable).orElse(null);
        }
    }

    private static final class Inference {
        private Inference() {}

        static List<OrderDelta.Update<BazaarDataOrigin>> run(
                @NotNull List<Order> orders, @NotNull Eligibility eligibility,
                @NotNull BookMutation.MutationOutcome outcome, @NotNull BazaarDataOrigin origin) {
            var data = outcome.book();
            if (data == null) return List.of();

            var observation = new Observation(data, outcome, origin);
            var eligible = eligibleOrders(orders, data, origin, eligibility, outcome);

            ImmutableListMultimap<PriceGroup, Order> grouped = Multimaps.index(
                    eligible, order -> new PriceGroup(order.side(), order.pricePerItem()));

            var result = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();
            for (var entry : grouped.asMap().entrySet()) {
                result.addAll(level(entry.getKey(), List.copyOf(entry.getValue()), observation));
            }

            return List.copyOf(result);
        }

        private static List<Order> eligibleOrders(
                @NotNull List<Order> orders, @NotNull ProductData data,
                @NotNull BazaarDataOrigin origin, @NotNull Eligibility eligibility,
                @NotNull BookMutation.MutationOutcome outcome) {
            return orders.stream()
                    .filter(order -> order.productId().equals(data.getProductId()))
                    .filter(Order::isOpen)
                    .filter(order -> order.effectiveExpiresAt() > origin.timestamp())
                    .filter(order -> eligibility.accepts(order, outcome))
                    .toList();
        }

        private static List<OrderDelta.Update<BazaarDataOrigin>> level(
                @NotNull PriceGroup group, @NotNull List<Order> orders,
                @NotNull Observation observation) {
            var type = TransactionType.of(group.side(), TransactionType.Method.ORDER).getPriceType();
            var level = observation.levelAt(group);
            var sorted = orders.stream().sorted(Order.byFillPriority(group.side())).toList();

            return level == null
                    ? vanished(group, type, sorted, observation)
                    : volumeDelta(group, sorted, level, observation.origin());
        }

        private static List<OrderDelta.Update<BazaarDataOrigin>> vanished(
                @NotNull PriceGroup group, @NotNull PriceType type, @NotNull List<Order> sorted,
                @NotNull Observation observation) {
            if (!observation.outcome.canEvict(type, group.price())) {
                return List.of();
            }

            int totalRemaining = sorted.stream().mapToInt(Order::unfilledAmount).sum();

            Util.logMessage("%s — Fill inference: level vanished @ %.4f, %d order(s), Δ%d".formatted(
                    observation.origin().describe(), group.price(), sorted.size(), totalRemaining));

            return allocateFifo(sorted, totalRemaining, observation.origin());
        }

        private static List<OrderDelta.Update<BazaarDataOrigin>> volumeDelta(
                @NotNull PriceGroup group, @NotNull List<Order> sorted, @NotNull PriceLevel<?> level,
                @NotNull BazaarDataOrigin origin) {
            int totalExpected = sorted.stream().mapToInt(Order::unfilledAmount).sum();

            if (level.orderCount() != sorted.size()) return List.of();

            int totalDelta = totalExpected - (int) level.totalVolume();
            if (totalDelta <= 0) return List.of();

            Util.logMessage("%s — Fill inference: level delta @ %.4f, %d order(s), Δ%d".formatted(
                    origin.describe(), group.price(), sorted.size(), totalDelta));

            return allocateFifo(sorted, totalDelta, origin);
        }

        private static List<OrderDelta.Update<BazaarDataOrigin>> allocateFifo(
                @NotNull List<Order> sortedByQueue, int totalDelta, @NotNull BazaarDataOrigin origin) {
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
    }

    private static final class Settlement {
        private Settlement() {}

        static @NotNull Set<String> settle(
                @NotNull Collection<BookMutation.MutationOutcome> outcomes,
                @NotNull BazaarDataOrigin origin, @NotNull Eligibility eligibility) {
            var allKnown = UserOrdersStorage.allKnown();
            if (allKnown.isEmpty()) return Set.of();

            List<Order> union = allKnown.values().stream().flatMap(List::stream).toList();

            ImmutableMap<UUID, ProfileKey> ownerOf = allKnown.entrySet().stream()
                    .flatMap(e -> e.getValue().stream().map(order -> Maps.immutableEntry(order.id(), e.getKey())))
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

            var allUpdates = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();
            for (var outcome : outcomes) {
                allUpdates.addAll(Inference.run(union, eligibility, outcome, origin));
            }
            if (allUpdates.isEmpty()) return Set.of();

            ImmutableListMultimap<ProfileKey, OrderDelta.Update<BazaarDataOrigin>> byOwner =
                    Multimaps.index(allUpdates, update -> ownerOf.get(update.before().id()));

            byOwner.asMap().forEach((key, updates) -> Persistence.apply(key, List.copyOf(updates), origin));

            return allUpdates.stream().map(update -> update.before().productId()).collect(Collectors.toUnmodifiableSet());
        }

        static @NotNull List<Order> applyAndSettle(
                @NotNull ProfileKey key, @NotNull Collection<BookMutation.MutationOutcome> outcomes,
                @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation) {
            var result = UserOrdersStorage.apply(key, operation);

            settle(outcomes, origin, Eligibility.ALWAYS);

            return result;
        }
    }

    private static final class Persistence {
        private Persistence() {}

        static boolean apply(
                @NotNull ProfileKey key,
                @NotNull List<OrderDelta.Update<BazaarDataOrigin>> inferences,
                @NotNull BazaarDataOrigin origin) {
            if (inferences.isEmpty()) return false;

            var beforeByOrderId = inferences.stream().collect(Collectors.toMap(it -> it.before().id(), OrderDelta.Update::before));
            var fillMap = inferences.stream().collect(Collectors.toMap(it -> it.before().id(), OrderDelta.Update::after));

            UserOrdersStorage.StorageOp operation = current -> current.stream()
                    .map(order -> fillMap.getOrDefault(order.id(), order))
                    .collect(Collectors.toCollection(ArrayList::new));

            Util.logMessage("Applying Δ%d inferred fills [%s]".formatted(fillMap.size(), key));

            var result = UserOrdersStorage.apply(key, operation.then(UserOrdersStorage.StorageOp.reindex()));

            result.stream().filter(order -> fillMap.containsKey(order.id())).forEach(after -> {
                var before = beforeByOrderId.get(after.id());
                int delta = after.filledAmount() - before.filledAmount();
                String status = after.isFilled() ? "fully filled" : "partially filled";

                OrderDelta.Update.UpdateKind.FILL.getEvent(before, after, key).post(BazaarUtils.EVENT_BUS);

                String msg = "%s — Fill advanced %d → %d (Δ%d, %s): %s".formatted(
                        origin.describe(), before.filledAmount(), after.filledAmount(), delta, status, after.describe());

                PlayerActionUtil.notifyAll(msg, NotificationType.ORDERDATA);
            });

            return true;
        }
    }
}