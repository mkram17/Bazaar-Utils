package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation.AttributedFills.Attribution;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deduces fill progress for tracked orders from how a product's book moved, and
 * commits whatever it deduces across however many profiles are actually affected.
 *
 * <p>{@link #settle} takes a batch of {@link BookMutation.MutationOutcome}s — one book
 * mutation tick can cover many products — and unions every known profile's orders
 * before grouping by price, so two different profiles resting at the same price on
 * the same product are still checked for exclusive occupancy together, not
 * separately. Results are split back out by owning profile only at the final write.
 *
 * <p>{@link #applyAndSettle} additionally performs one profile's own storage write
 * first, then runs the same settlement pass — for a caller that both needs to commit
 * its own change and wants whatever fill inference that change reveals to reach every
 * other known position too.
 */
public final class FillInference {
    public record Result(@NotNull Set<String> changedProducts, @NotNull Map<ProfileKey, List<Order>> committedByProfile) {

        public static @NotNull Result empty() {
            return new Result(Set.of(), Map.of());
        }

        /** {@code key}'s post-write list from this pass, or {@code fallback} if this pass didn't touch {@code key}. */
        public @NotNull List<Order> ordersOrElse(@NotNull ProfileKey key, @NotNull List<Order> fallback) {
            return committedByProfile.getOrDefault(key, fallback);
        }
    }

    private FillInference() {}

    /**
     * Infers fill progress from {@code outcomes} across every known profile's tracked
     * orders, persists whatever's found, and returns which products actually changed.
     */
    public static @NotNull Result settle(
            @NotNull Collection<BookMutation.MutationOutcome> outcomes,
            @NotNull BazaarDataOrigin origin, @NotNull Eligibility eligibility) {
        return Settlement.settle(outcomes, origin, eligibility);
    }

    /**
     * Applies {@code operation} to {@code key}'s storage, then infers and persists
     * fill progress from {@code outcomes} across every known profile. Returns the
     * post-persist list for {@code key} specifically.
     */
    public static @NotNull Result applyAndSettle(
            @NotNull ProfileKey key, @NotNull Collection<BookMutation.MutationOutcome> outcomes,
            @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation) {
        return Settlement.applyAndSettle(key, outcomes, origin, operation);
    }

    /**
     * Extra, caller-supplied filtering on top of the fixed rules every inference pass
     * already applies (matching product, open, not yet expired, not updated after this
     * observation). {@link #ALWAYS} imposes nothing further.
     */
    @FunctionalInterface
    public interface Eligibility {
        boolean accepts(@NotNull Order order, @NotNull BookMutation.MutationOutcome outcome);

        /** Accepts every order the fixed rules already let through. */
        Eligibility ALWAYS = (_, _) -> true;

        Eligibility SESSION = (order, outcome) -> eligibleOrder(order, outcome, TimeUtil.getModInitEpochMs());

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

    private static final class Inference {
        private Inference() {}

        /**
         * Groups {@code productOrders} (already scoped to {@code outcome}'s product) by
         * {@link PriceGroup} and infers a fill delta for each group independently.
         */
        static List<OrderDelta.Update<BazaarDataOrigin>> run(
                @NotNull List<Order> productOrders, @NotNull Eligibility eligibility,
                @NotNull BookMutation.MutationOutcome outcome, @NotNull BazaarDataOrigin origin) {
            var data = outcome.book();
            if (data == null) {
                Util.logMessage("%s — Fill inference: skipped, mutation outcome carries no registered book".formatted(origin.describe()));

                return List.of();
            }

            var eligible = eligibleOrders(productOrders, data, origin, eligibility, outcome);

            if (eligible.isEmpty()) {
                if (!productOrders.isEmpty()) {
                    Util.logMessage("%s — Fill inference: %s — every candidate order filtered out before grouping".formatted(
                            origin.describe(), data.getProductId()));
                }

                return List.of();
            }

            ImmutableListMultimap<PriceGroup, Order> grouped = Multimaps.index(
                    eligible, order -> new PriceGroup(TransactionType.of(order.side(), TransactionType.Method.ORDER).getPriceType(), order.pricePerItem()));

            var result = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();
            for (var entry : grouped.asMap().entrySet()) {
                result.addAll(level(entry.getKey(), List.copyOf(entry.getValue()), outcome, origin));
            }

            return List.copyOf(result);
        }

        /**
         * Narrows {@code productOrders} to this product, still open, not yet past its own
         * expiry, and not updated more recently than {@code origin} itself, before
         * finally applying whatever {@code eligibility} additionally requires.
         */
        private static List<Order> eligibleOrders(
                @NotNull List<Order> productOrders, @NotNull ProductData data,
                @NotNull BazaarDataOrigin origin, @NotNull Eligibility eligibility,
                @NotNull BookMutation.MutationOutcome outcome) {
            return productOrders.stream()
                    .filter(order -> order.productId().equals(data.getProductId()))
                    .filter(Order::isOpen)
                    .filter(order -> order.effectiveExpiresAt() > origin.timestamp())
                    // An order written after this observation cannot be evidenced by it —
                    // whatever the book shows, it predates what we already know.
                    .filter(order -> order.lastUpdatedAt() <= origin.timestamp())
                    .filter(order -> eligibility.accepts(order, outcome))
                    .toList();
        }

        /**
         * Resolves one price group's current level and its batch attribution metadata,
         * then dispatches to {@link #vanished} or {@link #volumeDelta} depending on
         * whether the level still exists.
         */
        private static List<OrderDelta.Update<BazaarDataOrigin>> level(
                @NotNull PriceGroup group, @NotNull List<Order> orders,
                @NotNull BookMutation.MutationOutcome outcome, @NotNull BazaarDataOrigin origin) {
            var sorted = orders.stream().sorted(Order.byFillPriority(group.type())).toList();
            var attribution = outcome.attributed().at(group);

            return switch (outcome.observe(group.type(), group.price())) {
                case BookMutation.MutationOutcome.LevelObservation.Present(var level) -> volumeDelta(group, sorted, level, attribution, origin);
                case BookMutation.MutationOutcome.LevelObservation.ProvenAbsent ignored -> vanished(group, sorted, attribution, origin);
                case BookMutation.MutationOutcome.LevelObservation.Unknown ignored -> {
                    Util.logMessage("%s — Fill inference: no entry @ %.4f, %d order(s), but read has no standing to call it absent".formatted(origin.describe(), group.price(), sorted.size()));

                    yield List.of();
                }
            };
        }

        /**
         * A price group whose level no longer resolves at all and is proven absent.
         * Every remaining unfilled unit across the group's still-open orders is distributed
         * via {@link #allocateFifo} (storage already reflects any direct fill attribution
         * committed earlier in this batch).
         */
        private static List<OrderDelta.Update<BazaarDataOrigin>> vanished(
                @NotNull PriceGroup group, @NotNull List<Order> sorted,
                @NotNull Attribution attribution, @NotNull BazaarDataOrigin origin) {
            long totalRemaining = sorted.stream().mapToLong(Order::unfilledAmount).sum();
            if (totalRemaining <= 0) {
                Util.logMessage("%s — Fill inference: level vanished @ %.4f, %d order(s), no shortfall (Δ%d attributed this batch)".formatted(
                        origin.describe(), group.price(), sorted.size(), attribution.volume()));

                return List.of();
            }

            Util.logMessage("%s — Fill inference: level vanished @ %.4f, %d order(s), Δ%d".formatted(
                    origin.describe(), group.price(), sorted.size(), totalRemaining));

            return allocateFifo(sorted, totalRemaining, attribution, origin);
        }

        /**
         * A price group whose level still exists. Requires exclusive occupancy —
         * {@code level.orderCount()} must equal the group's own size — since a level
         * shared with an untracked order gives no way to tell whose volume moved.
         * Because storage already reflects any direct fill attribution committed earlier
         * in this batch, {@code totalExpected - level.totalVolume()} is the exact residual
         * shortfall to distribute via {@link #allocateFifo}.
         */
        private static List<OrderDelta.Update<BazaarDataOrigin>> volumeDelta(
                @NotNull PriceGroup group, @NotNull List<Order> sorted, @NotNull PriceLevel<?> level,
                @NotNull Attribution attribution, @NotNull BazaarDataOrigin origin) {
            if (level.orderCount() != sorted.size()) {
                Util.logMessage("%s — Fill inference: skipped @ %.4f, level holds %d order(s) but %d tracked here — not exclusive".formatted(
                        origin.describe(), group.price(), level.orderCount(), sorted.size()));

                return List.of();
            }

            long totalExpected = sorted.stream().mapToLong(Order::unfilledAmount).sum();
            long totalDelta = totalExpected - level.totalVolume();
            if (totalDelta <= 0) {
                Util.logMessage("%s — Fill inference: no shortfall @ %.4f, %d order(s), expected=%d level=%d attributed=%d".formatted(
                        origin.describe(), group.price(), sorted.size(), totalExpected, level.totalVolume(), attribution.volume()));

                return List.of();
            }

            Util.logMessage("%s — Fill inference: level delta @ %.4f, %d order(s), Δ%d (Δ%d attributed this batch)".formatted(
                    origin.describe(), group.price(), sorted.size(), totalDelta, attribution.volume()));

            return allocateFifo(sorted, totalDelta, attribution, origin);
        }

        /**
         * Distributes {@code totalDelta} across {@code sortedByQueue} in order, capping
         * each order at its own remaining capacity.
         *
         * <p>Orders {@code attribution} doesn't include are filled first, in full, before any
         * leftover falls to one it does — so any unattributed shortfall belongs on a sibling
         * before it belongs back on an order this same batch already touched directly.
         */
        private static List<OrderDelta.Update<BazaarDataOrigin>> allocateFifo(
                @NotNull List<Order> sortedByQueue, long totalDelta,
                @NotNull Attribution attribution, @NotNull BazaarDataOrigin origin) {
            var results = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();

            // Unattributed orders first, in full; ones already attributed only for whatever's left over.
            var unattributed = sortedByQueue.stream().filter(order -> !attribution.includes(order.id())).toList();
            var alreadyAttributed = sortedByQueue.stream().filter(order -> attribution.includes(order.id())).toList();

            long remaining = totalDelta;
            for (var pass : List.of(unattributed, alreadyAttributed)) {
                for (Order order : pass) {
                    if (remaining <= 0) break;

                    int canFill = order.unfilledAmount();
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

        /**
         * Unions every known profile's orders indexed by {@code productId}, folds any
         * duplicate-product outcomes, runs inference only on products that actually have
         * tracked orders, and splits the resulting updates back out by owning profile at
         * the final {@link Persistence#apply} call.
         */
        static @NotNull Result settle(
                @NotNull Collection<BookMutation.MutationOutcome> outcomes,
                @NotNull BazaarDataOrigin origin, @NotNull Eligibility eligibility) {
            if (outcomes.isEmpty()) return Result.empty();

            var allKnown = UserOrdersStorage.allKnown();
            if (allKnown.isEmpty()) return Result.empty();

            List<Order> union = allKnown.values().stream().flatMap(List::stream).toList();
            if (union.isEmpty()) return Result.empty();

            ImmutableListMultimap<String, Order> ordersByProduct = Multimaps.index(union, Order::productId);

            // Fold outcomes by productId and skip any product with no tracked orders (avoids O(1500 × orders) scans).
            Map<String, BookMutation.MutationOutcome> outcomesByProduct = new LinkedHashMap<>();
            for (var outcome : outcomes) {
                var book = outcome.book();
                if (book == null) continue;

                String productId = book.getProductId();
                if (!ordersByProduct.containsKey(productId)) continue;

                outcomesByProduct.merge(productId, outcome, BookMutation.MutationOutcome::merge);
            }
            if (outcomesByProduct.isEmpty()) return Result.empty();

            Map<UUID, ProfileKey> ownerOf = new HashMap<>();
            allKnown.forEach((profile, orders) -> {
                for (Order order : orders) {
                    ownerOf.putIfAbsent(order.id(), profile);
                }
            });

            var allUpdates = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();
            for (var entry : outcomesByProduct.entrySet()) {
                var productOrders = ordersByProduct.get(entry.getKey());
                allUpdates.addAll(Inference.run(productOrders, eligibility, entry.getValue(), origin));
            }
            if (allUpdates.isEmpty()) return Result.empty();

            ImmutableListMultimap<ProfileKey, OrderDelta.Update<BazaarDataOrigin>> byOwner =
                    Multimaps.index(allUpdates, update -> ownerOf.get(update.before().id()));

            var committed = new HashMap<ProfileKey, List<Order>>();
            byOwner.asMap().forEach((owner, updates) ->
                    Persistence.apply(owner, List.copyOf(updates), origin)
                            .ifPresent(list -> committed.put(owner, list)));

            var changedProducts = allUpdates.stream()
                    .map(update -> update.before().productId())
                    .collect(Collectors.toUnmodifiableSet());

            return new Result(changedProducts, Map.copyOf(committed));
        }

        /**
         * Writes {@code operation} for {@code key} directly, then runs a full
         * {@link #settle} pass with no further eligibility restriction — the write just
         * committed is itself one of the mutations {@code outcomes} may need settling
         * against.
         */
        static @NotNull Result applyAndSettle(
                @NotNull ProfileKey key, @NotNull Collection<BookMutation.MutationOutcome> outcomes,
                @NotNull BazaarDataOrigin.UserPositionEvent origin, @NotNull UserOrdersStorage.StorageOp operation) {
            List<Order> primary = UserOrdersStorage.apply(key, operation);
            Result settled = settle(outcomes, origin, Eligibility.ALWAYS);

            if (settled.committedByProfile().containsKey(key)) return settled;

            var merged = new HashMap<>(settled.committedByProfile());
            merged.put(key, primary);

            return new Result(settled.changedProducts(), Map.copyOf(merged));
        }
    }

    private static final class Persistence {
        private Persistence() {}

        /**
         * Writes every inferred before/after pair for {@code key} in one atomic storage
         * op, always followed by a full reindex — one group's FIFO distribution can
         * advance more than one order to fully filled in the same pass, so a blanket
         * reindex is simpler than conditionally checking each one. Fires
         * {@link OrderDelta.Update.UpdateKind#FILL}'s event per actually-updated order.
         */
        static Optional<List<Order>> apply(
                @NotNull ProfileKey key,
                @NotNull List<OrderDelta.Update<BazaarDataOrigin>> inferences,
                @NotNull BazaarDataOrigin origin) {
            if (inferences.isEmpty()) return Optional.empty();

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

            return Optional.of(result);
        }
    }
}
