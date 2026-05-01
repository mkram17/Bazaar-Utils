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
        return settle(outcomes, origin, eligibility, Set.of());
    }

    /**
     * {@link #settle(Collection, BazaarDataOrigin, Eligibility)}, preferring an order not
     * named in {@code confirmed} when a residual shortfall has to land somewhere.
     */
    public static @NotNull Set<String> settle(
            @NotNull Collection<BookMutation.MutationOutcome> outcomes,
            @NotNull BazaarDataOrigin origin, @NotNull Eligibility eligibility, @NotNull Set<UUID> confirmed) {
        return Settlement.settle(outcomes, origin, eligibility, confirmed);
    }

    public static @NotNull List<Order> applyAndSettle(
            @NotNull ProfileKey key, @NotNull Collection<BookMutation.MutationOutcome> outcomes,
            @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation) {
        return applyAndSettle(key, outcomes, origin, operation, Set.of());
    }

    /**
     * {@link #applyAndSettle(ProfileKey, Collection, BazaarDataOrigin.UserPositionEvent, UserOrdersStorage.StorageOp)},
     * netting {@code confirmed}'s withdrawn volume out of this pass's own shortfall
     * calculation.
     */
    public static @NotNull List<Order> applyAndSettle(
            @NotNull ProfileKey key, @NotNull Collection<BookMutation.MutationOutcome> outcomes,
            @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation,
            @NotNull Set<UUID> confirmed) {
        return Settlement.applyAndSettle(key, outcomes, origin, operation, confirmed);
    }

    @FunctionalInterface
    public interface Eligibility {
        boolean accepts(@NotNull Order order, @NotNull BookMutation.MutationOutcome outcome);

        Eligibility ALWAYS = (_, _) -> true;
    }

    private record Observation(
            @NotNull ProductData book,
            @NotNull BookMutation.MutationOutcome outcome,
            @NotNull BazaarDataOrigin origin) {
        @Nullable
        PriceLevel<?> levelAt(@NotNull PriceGroup group) {
            return book.entryAt(group.type(), group.price()).flatMap(LevelReconciliation::tradable).orElse(null);
        }
    }

    private static final class Inference {
        private Inference() {}

        static List<OrderDelta.Update<BazaarDataOrigin>> run(
                @NotNull List<Order> orders, @NotNull Eligibility eligibility,
                @NotNull BookMutation.MutationOutcome outcome, @NotNull BazaarDataOrigin origin,
                @NotNull Set<UUID> confirmed) {
            var data = outcome.book();
            if (data == null) {
                Util.logMessage("%s — Fill inference: skipped, mutation outcome carries no registered book".formatted(origin.describe()));

                return List.of();
            }

            var observation = new Observation(data, outcome, origin);
            var eligible = eligibleOrders(orders, data, origin, eligibility, outcome);

            if (eligible.isEmpty()) {
                boolean hadCandidates = orders.stream().anyMatch(order -> order.productId().equals(data.getProductId()));

                if (hadCandidates) {
                    Util.logMessage("%s — Fill inference: %s — every candidate order filtered out before grouping".formatted(
                            origin.describe(), data.getProductId()));
                }

                return List.of();
            }

            ImmutableListMultimap<PriceGroup, Order> grouped = Multimaps.index(
                    eligible, order -> new PriceGroup(TransactionType.of(order.side(), TransactionType.Method.ORDER).getPriceType(), order.pricePerItem()));

            var result = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();
            for (var entry : grouped.asMap().entrySet()) {
                result.addAll(level(entry.getKey(), List.copyOf(entry.getValue()), observation, confirmed));
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
                    // An order written after this observation cannot be evidenced by it —
                    // whatever the book shows, it predates what we already know.
                    .filter(order -> order.lastUpdatedAt() <= origin.timestamp())
                    .filter(order -> eligibility.accepts(order, outcome))
                    .toList();
        }

        private static List<OrderDelta.Update<BazaarDataOrigin>> level(
                @NotNull PriceGroup group, @NotNull List<Order> orders,
                @NotNull Observation observation, @NotNull Set<UUID> confirmed) {
            var level = observation.levelAt(group);
            var sorted = orders.stream().sorted(Order.byFillPriority(group.type())).toList();
            long attributed = observation.outcome().attributed().at(group.type(), group.price());

            return level == null
                    ? vanished(group, group.type(), sorted, observation, attributed, confirmed)
                    : volumeDelta(group, sorted, level, observation.origin(), attributed, confirmed);
        }

        private static List<OrderDelta.Update<BazaarDataOrigin>> vanished(
                @NotNull PriceGroup group, @NotNull PriceType type, @NotNull List<Order> sorted,
                @NotNull Observation observation, long attributed, @NotNull Set<UUID> confirmed) {
            if (!observation.outcome.canEvict(type, group.price())) {
                Util.logMessage("%s — Fill inference: no entry @ %.4f, %d order(s), but read has no standing to call it absent".formatted(
                        observation.origin().describe(), group.price(), sorted.size()));

                return List.of();
            }

            long totalRemaining = sorted.stream().mapToLong(Order::unfilledAmount).sum() - attributed;
            if (totalRemaining <= 0) {
                Util.logMessage("%s — Fill inference: level vanished @ %.4f, %d order(s), no shortfall (Δ%d attributed this batch)".formatted(
                        observation.origin().describe(), group.price(), sorted.size(), attributed));

                return List.of();
            }

            Util.logMessage("%s — Fill inference: level vanished @ %.4f, %d order(s), Δ%d".formatted(
                    observation.origin().describe(), group.price(), sorted.size(), totalRemaining));

            return allocateFifo(sorted, totalRemaining, observation.origin(), confirmed);
        }

        private static List<OrderDelta.Update<BazaarDataOrigin>> volumeDelta(
                @NotNull PriceGroup group, @NotNull List<Order> sorted, @NotNull PriceLevel<?> level,
                @NotNull BazaarDataOrigin origin, long attributed, @NotNull Set<UUID> confirmed) {
            if (level.orderCount() != sorted.size()) {
                Util.logMessage("%s — Fill inference: skipped @ %.4f, level holds %d order(s) but %d tracked here — not exclusive".formatted(
                        origin.describe(), group.price(), level.orderCount(), sorted.size()));

                return List.of();
            }

            long totalExpected = sorted.stream().mapToLong(Order::unfilledAmount).sum();
            long totalDelta = totalExpected - level.totalVolume() - attributed;
            if (totalDelta <= 0) {
                Util.logMessage("%s — Fill inference: no shortfall @ %.4f, %d order(s), expected=%d level=%d attributed=%d".formatted(
                        origin.describe(), group.price(), sorted.size(), totalExpected, level.totalVolume(), attributed));

                return List.of();
            }

            Util.logMessage("%s — Fill inference: level delta @ %.4f, %d order(s), Δ%d (Δ%d already attributed this batch)".formatted(origin.describe(), group.price(), sorted.size(), totalDelta, attributed));

            return allocateFifo(sorted, totalDelta, origin, confirmed);
        }

        private static List<OrderDelta.Update<BazaarDataOrigin>> allocateFifo(
                @NotNull List<Order> sortedByQueue, long totalDelta,
                @NotNull BazaarDataOrigin origin, @NotNull Set<UUID> confirmed) {
            var results = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();

            // Untouched orders first, touched ones only as a fallback for whatever's left —
            // a deliberate departure from strict fill-priority order, not an oversight. By
            // the time totalDelta reaches here it's already net of everything this batch's
            // own deltas explained, so what's left is either genuinely independent or a rare
            // case our own accounting couldn't fully place; either way it's better resolved
            // against a sibling than pinned back on an order just confirmed this same batch.
            var untouched = sortedByQueue.stream().filter(order -> !confirmed.contains(order.id())).toList();
            var touched = sortedByQueue.stream().filter(order -> confirmed.contains(order.id())).toList();

            long remaining = totalDelta;
            for (var pass : List.of(untouched, touched)) {
                for (Order order : pass) {
                    if (remaining <= 0) break;

                    int canFill = Math.max(0, order.originalAmount() - order.filledAmount());
                    int delta = (int) Math.min(remaining, canFill);
                    if (delta <= 0) continue;

                    results.add(OrderDelta.Update.fill(order, order.withFill(delta, origin), BookMutation.none()));
                    remaining -= delta;
                }
            }

            return results;
        }
    }

    private static final class Settlement {
        private Settlement() {}

        static @NotNull Set<String> settle(
                @NotNull Collection<BookMutation.MutationOutcome> outcomes,
                @NotNull BazaarDataOrigin origin, @NotNull Eligibility eligibility,
                @NotNull Set<UUID> confirmed) {
            var allKnown = UserOrdersStorage.allKnown();
            if (allKnown.isEmpty()) return Set.of();

            List<Order> union = allKnown.values().stream().flatMap(List::stream).toList();

            ImmutableMap<UUID, ProfileKey> ownerOf = allKnown.entrySet().stream()
                    .flatMap(e -> e.getValue().stream().map(order -> Maps.immutableEntry(order.id(), e.getKey())))
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

            var allUpdates = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();
            for (var outcome : outcomes) {
                allUpdates.addAll(Inference.run(union, eligibility, outcome, origin, confirmed));
            }
            if (allUpdates.isEmpty()) return Set.of();

            ImmutableListMultimap<ProfileKey, OrderDelta.Update<BazaarDataOrigin>> byOwner =
                    Multimaps.index(allUpdates, update -> ownerOf.get(update.before().id()));

            byOwner.asMap().forEach((key, updates) -> Persistence.apply(key, List.copyOf(updates), origin));

            return allUpdates.stream().map(update -> update.before().productId()).collect(Collectors.toUnmodifiableSet());
        }

        static @NotNull List<Order> applyAndSettle(
                @NotNull ProfileKey key, @NotNull Collection<BookMutation.MutationOutcome> outcomes,
                @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation,
                @NotNull Set<UUID> confirmed) {
            var result = UserOrdersStorage.apply(key, operation);

            settle(outcomes, origin, Eligibility.ALWAYS, confirmed);

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