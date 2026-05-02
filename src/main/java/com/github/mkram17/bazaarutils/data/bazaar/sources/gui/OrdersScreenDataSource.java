package com.github.mkram17.bazaarutils.data.bazaar.sources.gui;

import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.SnapshotSource;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderSlotPosition;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.google.common.collect.Maps;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Reconciles all tracked orders against the live Bazaar orders screen.
 *
 * <p>The screen is the single source of truth for:
 * <ul>
 *   <li>Order existence — absent orders are evicted (unless updated after observedAt)</li>
 *   <li>Slot position — {@code lastKnownIndex} is set directly from the screen; no reindex</li>
 *   <li>Fill floor — screen fill is authoritative upward; we never regress</li>
 *   <li>Claim floor — screen claimedAmount is authoritative upward; we never regress</li>
 *   <li>New orders — synthesized on sight if no tracked match exists</li>
 * </ul>
 * Terminal states (Filled, Claimed, Cancelled) are never downgraded by a screen observation.
 *
 * <p>This class owns resolution and mutation composition only. All infrastructure —
 * book apply, floor affirmation, storage write, event dispatch, persist, and
 * {@link com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent} —
 * is delegated to {@link SnapshotSource#commitAll}.
 *
 * <p>Resolution is structured around {@link Maps#difference} keyed by order UUID:
 * <ul>
 *   <li>{@code entriesOnlyOnLeft}  — stored orders absent from screen
 *       → {@link OrderDelta.Evict} | preserved | off-screen</li>
 *   <li>{@code entriesOnlyOnRight} — synthesized orders
 *       → {@link OrderDelta.Place}</li>
 *   <li>{@code entriesDiffering}   — matched orders whose state advanced
 *       → {@link OrderDelta.Update} (fill/claim) or {@link OrderDelta.PriceCorrection}</li>
 *   <li>{@code entriesInCommon}    — matched, unchanged — no delta produced</li>
 * </ul>
 *
 * Book mutations for evictions, fill decrements, price corrections, and floor
 * affirmation are composed into one {@link BookMutation} chain and passed as a
 * separate parameter to {@link SnapshotSource#commitAll}, alongside the delta list.
 */
@DataSource
public final class OrdersScreenDataSource extends SnapshotSource {
    private static final BazaarLogger LOG = BazaarLogger.of(OrdersScreenDataSource.class);

    public BazaarLogger log() {
        return LOG;
    }

    private static final long EVICTION_GRACE_MS = 600;

    public OrdersScreenDataSource() {}

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    public void onContainerLoaded(ContainerLoadedEvent event) {
        var storage = UserOrdersStorage.get();
        if (storage == null) return;

        var origin = new BazaarDataOrigin.OrdersScreen(System.currentTimeMillis());

        List<ItemInfo> items = event.getContainerSlots().stream()
                .map(slot -> new ItemInfo(slot.getContainerSlot(), slot.getItem()))
                .toList();

        String playerName = Minecraft.getInstance().getUser().getName();
        List<PageOrderParser.ParsedEntry> parsed = PageOrderParser.parse(items, event.getContainerSlots().size(), playerName);

        Map<String, List<PageOrderParser.ParsedEntry>> byProduct = new LinkedHashMap<>();
        parsed.forEach(entry -> byProduct
                .computeIfAbsent(entry.info().getProductId(), k -> new ArrayList<>())
                .add(entry));

        int totalActive = (int) storage.stream().filter(Order::isLive).count();
        boolean canOverflow = totalActive > OrdersPageLayout.SCREEN_CAPACITY;

        // Evict products tracked but entirely absent from the screen.
        storage.stream()
                .map(Order::productId)
                .distinct()
                .filter(id -> !byProduct.containsKey(id))
                .filter(id -> !canOverflow || storage.stream()
                        .filter(order -> order.productId().equals(id))
                        .allMatch(Order::isVisible))
                .filter(id -> storage.stream()
                        .filter(order -> order.productId().equals(id))
                        .allMatch(order -> (origin.observedAt() - order.lastUpdatedAt()) > EVICTION_GRACE_MS))
                .forEach(id -> {
                    PlayerLogger.debug("%s — Evicting product absent from screen: %s".formatted(origin.describe(), id), NotificationType.ORDER_LIFECYCLE, LOG);

                    reconcileProduct(id, List.of(), origin);
                });

        byProduct.forEach((productId, entries) -> reconcileProduct(productId, entries, origin));
    }

    /**
     * Resolves one product's screen state into a {@link List} of {@link OrderDelta}s
     * and delegates to {@link #commitAll}.
     *
     * <p>Owns phases 1–3 only:
     * <ol>
     *   <li>Match screen entries against stored orders; build before/after maps.</li>
     *   <li>Classify unmatched stored orders via {@link Maps#difference}.</li>
     *   <li>Compose the {@link BookMutation} chain and build the delta list.</li>
     * </ol>
     */
    private void reconcileProduct(String productId, List<PageOrderParser.ParsedEntry> entries, BazaarDataOrigin.OrdersScreen origin) {
        var storage = UserOrdersStorage.orders();

        var unrelated = storage.stream()
                .filter(order -> !order.productId().equals(productId))
                .toList();

        // UUID-keyed for O(1) lookup and Maps.difference.
        Map<UUID, Order> storedById = storage.stream()
                .filter(order -> order.productId().equals(productId))
                .collect(Collectors.toMap(Order::id, order -> order));

        // ── Phase 1: match screen entries, build reconciled state ────────────
        var usedIds = new HashSet<UUID>();
        var reconciledById = new LinkedHashMap<UUID, Order>();   // matched+synthesized (diff right)

        for (var entry : entries) {
            var match = findMatch(storedById, usedIds, entry);

            if (match != null) {
                usedIds.add(match.id());
                var after = reconcileExisting(match, entry, origin);

                reconciledById.put(match.id(), after);

                if (!match.slotPosition().equals(after.slotPosition())) {
                    PlayerLogger.debug("%s — Reanchored %s → %s: %s".formatted(
                            origin.describe(),
                            match.slotPosition().describe(), after.slotPosition().describe(),
                            after.describe()), NotificationType.ORDER_LIFECYCLE, LOG);
                }
            } else {
                var synth = synthesizeNew(entry, productId, origin);
                reconciledById.put(synth.id(), synth);

                PlayerLogger.debug("%s — Synthesized untracked order: %s".formatted(origin.describe(), synth.describe()), NotificationType.ORDER_LIFECYCLE, LOG);
            }
        }

        // ── Phase 2: classify unmatched stored orders via Maps.difference ────
        //
        //   Left  = storedById     (before-state for this product)
        //   Right = reconciledById (after-state: matched + synthesized)
        //
        //   entriesOnlyOnLeft  → not matched by screen → Evict | preserved | off-screen
        //   entriesOnlyOnRight → synthesized (fresh UUID absent from storage) → Place
        //   entriesDiffering   → matched orders whose state advanced → Update | PriceCorrection
        //   entriesInCommon    → matched, unchanged — no delta
        var diff = Maps.difference(storedById, reconciledById);

        var preserved = new ArrayList<Order>();
        var offScreen  = new ArrayList<Order>();

        // Deltas produced this reconciliation — BookOnly goes in last (after chain is fully composed).
        var deltas = new ArrayList<OrderDelta>();

        // Book mutation chain — composed incrementally, sealed into BookOnly at the end.
        BookMutation mutation = BookMutation.NONE;

        // ── Phase 3a: classify entriesOnlyOnLeft ─────────────────────────────
        for (var kv : diff.entriesOnlyOnLeft().entrySet()) {
            var order = kv.getValue();

            if (order.lastUpdatedAt() > origin.observedAt()) {
                PlayerLogger.debug("%s — Skipped — updated after observation window: %s".formatted(origin.describe(), order.describe()), NotificationType.ORDER_LIFECYCLE, LOG);

                preserved.add(order);

            } else if (!order.isVisible()) {
                PlayerLogger.debug("%s — Skipped — off-screen unanchored: %s".formatted(origin.describe(), order.describe()), NotificationType.ORDER_LIFECYCLE, LOG);

                offScreen.add(order);

            } else if (order.isLive() && (origin.observedAt() - order.lastUpdatedAt()) > EVICTION_GRACE_MS) {

                if (order.isFilled()) {
                    // Auto-claim: order left screen in filled state.
                    var claimed = order.withClaim(order.unclaimedFilled(), origin);
                    deltas.add(OrderDelta.Update.claim(order, claimed));

                    PlayerLogger.debug("%s — Auto-claimed %d units (left screen filled): %s".formatted(origin.describe(), order.unclaimedFilled(), order.describe()), NotificationType.ORDER_LIFECYCLE, LOG);
                } else {
                    // Cancelled eviction: decrement the book.
                    var cancelMutation = BookMutation.decrement(
                            TransactionType.of(order.side(), TransactionType.Method.ORDER),
                            order.pricePerItem(),
                            order.originalAmount() - order.claimedAmount(),
                            true);

                    mutation = mutation.then(cancelMutation);
                    deltas.add(new OrderDelta.Evict(order.cancelled(origin), cancelMutation));

                    PlayerLogger.debug("%s — Cancelled — disappeared from screen: %s".formatted(
                            origin.describe(), order.describe()), NotificationType.ORDER_LIFECYCLE, LOG);

                    PlayerLogger.debug("%s — Book decrement: %s %s Δ%d @ %.4f (evicted)".formatted(
                                    origin.describe(),
                                    TransactionType.of(order.side(), TransactionType.Method.ORDER).getPriceType(),
                                    productId, order.originalAmount() - order.claimedAmount(), order.pricePerItem()),
                            NotificationType.PRICE_DATA, LOG);
                }
            } else {
                // Terminal or inside grace period — retain without change.
                preserved.add(order);
            }
        }

        // ── Phase 3b: synthesized orders ─────────────────────────────────────
        for (var order : diff.entriesOnlyOnRight().values()) {
            deltas.add(new OrderDelta.Place(order, BookMutation.NONE));
        }

        // unchanged = entriesInCommon only — reconcileExisting returned `found` unchanged and the
        // same-reference contract places those orders here via Maps.difference.
        // Pure slot-reanchor orders are emitted as Reanchor deltas in Phase 3c so the
        // deltas.isEmpty() early-return in commitAll is bypassed and the updated slot persisted.
        var unchanged = new ArrayList<>(diff.entriesInCommon().values());

        // ── Phase 3c: differing matched orders ───────────────────────────────
        for (var entry : diff.entriesDiffering().entrySet()) {
            var before = entry.getValue().leftValue();
            var after = entry.getValue().rightValue();

            if (Double.compare(before.pricePerItem(), after.pricePerItem()) != 0) {
                // Price correction: remove stale level, place at corrected price.
                var repriceMutation = BookMutation.decrement(
                                TransactionType.of(before.side(), TransactionType.Method.ORDER),
                                before.pricePerItem(), before.unfilledAmount(), true)
                        .then(BookMutation.place(
                                TransactionType.of(after.side(), TransactionType.Method.ORDER),
                                after.pricePerItem(), after.unfilledAmount()));

                mutation = mutation.then(repriceMutation);
                deltas.add(new OrderDelta.PriceCorrection(before, after, repriceMutation));

                PlayerLogger.debug("%s — Price corrected %.4f → %.4f: %s".formatted(
                        origin.describe(), before.pricePerItem(), after.pricePerItem(),
                        after.describe()), NotificationType.ORDER_LIFECYCLE, LOG);

                PlayerLogger.debug("%s — Book re-priced: %s %s Δ%d @ %.4f → %.4f".formatted(
                                origin.describe(),
                                TransactionType.of(before.side(), TransactionType.Method.ORDER).getPriceType(),
                                productId, before.unfilledAmount(), before.pricePerItem(), after.pricePerItem()),
                        NotificationType.PRICE_DATA, LOG);
            } else if (after.filledAmount() > before.filledAmount()) {
                int fillDelta = after.filledAmount() - before.filledAmount();
                var fillMutation = BookMutation.decrement(
                        TransactionType.of(after.side(), TransactionType.Method.ORDER),
                        after.pricePerItem(), fillDelta, false);

                mutation = mutation.then(fillMutation);
                deltas.add(OrderDelta.Update.fill(before, after, fillMutation));

                PlayerLogger.debug("%s — Fill advanced %d → %d (Δ%d): %s".formatted(
                        origin.describe(), before.filledAmount(), after.filledAmount(),
                        fillDelta, after.describe()), NotificationType.ORDER_LIFECYCLE, LOG);

                PlayerLogger.debug("%s — Book decrement: %s %s Δ%d @ %.4f (fill advance)".formatted(
                                origin.describe(),
                                TransactionType.of(after.side(), TransactionType.Method.ORDER).getPriceType(),
                                productId, fillDelta, after.pricePerItem()),
                        NotificationType.PRICE_DATA, LOG);

            } else if (after.claimedAmount() > before.claimedAmount()) {
                // Claim advance with no concurrent fill change.
                deltas.add(OrderDelta.Update.claim(before, after));
            } else {
                // Pure slot reanchor — screen position changed, no state mutation.
                // No book mutation, no event. Reanchor delta carries `after` (updated
                // OnScreen slot) into the storage write and bypasses the deltas.isEmpty()
                // early-return in commitAll that was silently dropping the position update.
                deltas.add(new OrderDelta.Reanchor(before, after));
            }
        }

        // ── Phase 3d: floor affirmation via BookMutation.Floor ────────────────
        // Composed into the mutation chain — not handled by commitAll separately.
        // Runs unconditionally: a level can be lost through a snapshot eviction
        // with no corresponding order-state change and must be restored regardless.
        for (var side : TransactionType.Side.values()) {
            var transaction = TransactionType.of(side, TransactionType.Method.ORDER);
            var levels = reconciledById.values().stream()
                    .filter(Order::isActive)
                    .filter(order -> order.side() == side && order.unfilledAmount() > 0)
                    .map(order -> new PriceLevel(order.pricePerItem(), order.unfilledAmount(), 1, origin))
                    .toList();

            mutation = mutation.then(BookMutation.floor(transaction, levels));
        }

        commitAll(productId, mutation, deltas, unrelated, preserved, offScreen, unchanged, origin);
    }

    /**
     * Finds the best stored order to match against a screen entry using a three-tier
     * preference: exact slot index → active orders → any live order.
     *
     * <p>All tiers share the same base identity predicate (side + originalAmount +
     * similar price). Slot-index matching is preferred because it anchors reconciliation
     * to the screen's authoritative positional data; the active/live fallbacks handle
     * reanchoring after a slot shift.
     *
     * @return {@code null} when no stored order satisfies the base identity criteria.
     */
    private static @Nullable Order findMatch(Map<UUID, Order> candidates, Set<UUID> usedIds, PageOrderParser.ParsedEntry entry) {
        var info = entry.info();
        Predicate<Order> base = order ->
                !usedIds.contains(order.id())
                        && order.side() == info.getTransaction().getSide()
                        && order.originalAmount() == info.getVolume()
                        && info.isPriceSimilarTo(order.pricePerItem());

        return candidates.values().stream()
                .filter(base.and(order -> order.slotPosition().isOnScreenAt(entry.item().slotIndex())))
                .findFirst()
                .or(() -> candidates.values().stream().filter(base.and(Order::isActive)).findFirst())
                .or(() -> candidates.values().stream().filter(base).findFirst())
                // Filled coop orders fallback: price is unknown (stored as 0.0),
                // match on volume + side + flag only.
                .or(() -> candidates.values().stream()
                        .filter(order -> !usedIds.contains(order.id())
                                && order.coopOrder()
                                && order.side() == info.getTransaction().getSide()
                                && order.originalAmount() == info.getVolume()
                                && order.isFilled())
                        .findFirst())
                .orElse(null);
    }

    /**
     * Reconciles a matched order against its screen entry, returning either the
     * original reference (if nothing changed — load-bearing for Maps.difference
     * entriesInCommon classification) or a new Order with advanced state.
     *
     * <p>The same-reference contract: if every reconciled field equals the stored
     * field, {@code found} is returned unchanged so that {@link Maps#difference}
     * places it in {@code entriesInCommon} rather than {@code entriesDiffering}.
     * Never break this by returning a new instance with identical values.
     */
    private static Order reconcileExisting(Order found, PageOrderParser.ParsedEntry entry, BazaarDataOrigin.OrdersScreen origin) {
        int screenFill = Math.min(entry.filledAmount(), found.originalAmount());

        // k/M display noise in either direction; delta within FILL_TRUNCATION_MAX keeps stored.
        // Delta at or beyond it is real — take screen. Full fill: always originalAmount.
        int reconciledFill = screenFill < found.originalAmount()
                ? (Math.abs(found.filledAmount() - screenFill) < PageOrderParser.FILL_TRUNCATION_MAX
                   ? found.filledAmount()
                   : screenFill)
                : found.originalAmount();

        // Use reconciledFill (stored, accurate) minus exact claimable rather than
        // entry.claimedAmount() (= correctedFill - claimable). correctedFill is the
        // parser-side value anchored to screenFill, which can exceed reconciledFill
        // by up to FILL_TRUNCATION_MAX - 1 when screenFill lands exactly on a k-boundary
        // and stored fill lags; that gap leaks through as phantom claimed units.
        // reconciledFill - claimableAmount is clean when claimableAmount is exact (< 10k).
        // At or above 10k claimable is also truncated, making claimableAmount < trueClaimable
        // and reconciledFill - claimableAmount > trueClaimed — gate that out entirely.
        int reconciledClaimed;
        if (entry.claimableAmount() < PageOrderParser.CLAIM_TRUNCATION_THRESHOLD) {
            int screenClaimed = Math.clamp(reconciledFill - entry.claimableAmount(), 0, reconciledFill);
            reconciledClaimed = Math.clamp(found.claimedAmount(), screenClaimed, reconciledFill);
        } else {
            reconciledClaimed = Math.min(found.claimedAmount(), reconciledFill);
        }

        OrderStatus reconciledStatus;
        if (reconciledFill >= entry.info().getVolume()) {
            // Preserve the original Filled timestamp when already terminal.
            reconciledStatus = found.status() instanceof OrderStatus.Filled
                    ? found.status()
                    : new OrderStatus.Filled(origin.observedAt());
        } else if (reconciledFill > 0) {
            if (found.status() instanceof OrderStatus.Partial existing) {
                reconciledStatus = reconciledFill > found.filledAmount()
                        ? new OrderStatus.Partial(existing.firstFilledAt(), origin.observedAt())
                        : existing;
            } else {
                reconciledStatus = new OrderStatus.Partial(origin.observedAt(), origin.observedAt());
            }
        } else {
            // Screen shows nothing filled — this corrects any wrongly-set Filled
            // state that slipped through (e.g. a bad match on a original screen load).
            reconciledStatus = found.status() instanceof OrderStatus.Set
                    ? found.status()
                    : new OrderStatus.Set();
        }

        // Same-reference return — Maps.difference entriesInCommon relies on this.
        if (found.slotPosition().isOnScreenAt(entry.item().slotIndex())
                && found.filledAmount() == reconciledFill
                && found.claimedAmount() == reconciledClaimed
                && found.status() == reconciledStatus
                && Double.compare(found.pricePerItem(), entry.info().getPricePerItem()) == 0) {
            return found;
        }

        // Chat rounds total coins to integers above FOLDING_THRESHOLD, making
        // pricePerItem = totalCoins/volume imprecise. The screen shows the exact
        // fractional price — adopt it once reconciliation has confirmed the match.
        double reconciledPrice = (found.pricePerItem() * found.originalAmount() >= OrderInfo.FOLDING_THRESHOLD)
                ? entry.info().getPricePerItem()
                : found.pricePerItem();

        return new Order(
                found.id(), found.productId(),
                found.side(),
                reconciledPrice,
                found.originalAmount(),
                reconciledFill, reconciledClaimed,
                new OrderSlotPosition.OnScreen(entry.item().slotIndex()),
                reconciledStatus,
                found.placedAt(), origin.observedAt(), found.coopOrder());
    }

    private static Order synthesizeNew(PageOrderParser.ParsedEntry entry, String productId, BazaarDataOrigin.OrdersScreen origin) {
        var info = entry.info();
        int filledAmount = entry.filledAmount();
        int claimedAmount = entry.claimedAmount();

        OrderStatus status;
        if (filledAmount >= info.getVolume()) {
            status = new OrderStatus.Filled(origin.observedAt());
        } else if (filledAmount > 0) {
            status = new OrderStatus.Partial(origin.observedAt(), origin.observedAt());
        } else {
            status = new OrderStatus.Set();
        }

        return new Order(
                UUID.randomUUID(), productId,
                info.getTransaction().getSide(),
                info.getPricePerItem(),
                info.getVolume(),
                filledAmount, claimedAmount,
                new OrderSlotPosition.OnScreen(entry.item().slotIndex()),
                status,
                origin.observedAt(), origin.observedAt(), entry.coopOrder());
    }
}