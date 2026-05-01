package com.github.mkram17.bazaarutils.data.bazaar.sources.gui;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.RenderedOrdersIndex;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.stored.BazaarProfileFlags;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.helpers.McPlayer;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Reconciles all tracked orders against the live Orders page on each container load.
 *
 * <p>The screen is the authoritative source for: order existence (absent orders past the
 * grace window are evicted), container slot position (stamped directly; no reindex), fill
 * and claim counts (floor only; stored value never regressed), and newly observed orders
 * (synthesized when no stored match exists).
 *
 * <p>Resolution runs in two phases before any storage write. Phase 1
 * ({@link #matchScreenEntries}) matches screen entries against stored orders; its
 * per-product outputs are assembled into a single slot map in {@link #onContainerLoaded}.
 * Phase 2–4 ({@link #reconcileProduct}) classifies unmatched stored orders against that
 * map — a slot occupied by another product's order disproves a cached position without
 * any separate verification step.
 *
 * <p>Both a screen-confirmed slot change (Phase 3c) and a disproven cached position
 * demoted to {@link OrderSlotPosition.OffScreen} (Phase 3a) are carried as
 * {@link OrderDelta.Reanchor}: no state changed, no event fires, but the write is never
 * silently skipped by {@link #commitAll}'s empty-deltas guard.
 *
 * <p>Per-product resolution via {@link com.google.common.collect.Maps#difference} keyed
 * by UUID:
 * {@code entriesOnlyOnLeft} → {@link OrderDelta.Evict}, preserved, off-screen, or
 * {@link OrderDelta.Reanchor} to {@link OrderSlotPosition.OffScreen};
 * {@code entriesOnlyOnRight} → {@link OrderDelta.Place};
 * {@code entriesDiffering} → {@link OrderDelta.Update}, {@link OrderDelta.PriceCorrection},
 * or {@link OrderDelta.Reanchor} to the confirmed {@link OrderSlotPosition.OnScreen} slot;
 * {@code entriesInCommon} → no delta.
 *
 * <p>All book mutations for a product are composed into one {@link BookMutation}
 * chain passed to {@link #stageCommit}. Storage, events, and
 * {@link com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent}
 * are handled by {@link #commitAll} — both owned directly by this class rather
 * than shared through a common base, since screen reconciliation's batching
 * requirement (see {@link #commitAll}) is unlike anything the snapshot sources
 * need.
 */
@DataSource
public final class OrdersScreenDataSource extends BUListener {
    /**
     * Minimum age of the last update, in milliseconds, before a stored order can be evicted by
     * screen absence. Guards against the race where a chat event (fill, claim, cancel) stamps
     * {@code lastUpdatedAt} nanoseconds before the container-loaded event fires — without this
     * window the freshly updated order would be immediately evicted.
     */
    private static final long EVICTION_GRACE_MS = 600;

    public OrdersScreenDataSource() {}

    @Subscription(priority = Priority.HIGHEST)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    public void onContainerLoaded(ContainerLoadedEvent event) {
        var origin = new BazaarDataOrigin.OrdersScreen(System.currentTimeMillis());

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        boolean isKnownCoop = BazaarProfileFlags.isKnownCoop(key) || event.getTitle().equals("Co-op Bazaar Orders");

        List<ItemInfo> items = event.getContainerSlots().stream()
                .map(slot -> new ItemInfo(slot.getContainerSlot(), slot.getItem()))
                .toList();

        String localPlayerName = McPlayer.INSTANCE.getName();

        List<PageOrderParser.ParsedEntry> parsed = PageOrderParser.parse(items, event.getContainerSlots().size(), key, localPlayerName, isKnownCoop, origin);

        if (!isKnownCoop && parsed.stream().anyMatch(entry -> entry.attribution().isCoopContext())) {
            BazaarProfileFlags.markObservedCoop(key);
        }

        Map<String, List<PageOrderParser.ParsedEntry>> byProduct = new LinkedHashMap<>();
        parsed.forEach(entry -> byProduct
                .computeIfAbsent(entry.info().getProductId(), k -> new ArrayList<>())
                .add(entry));

        // Every product that could need reconciling this tick: anything on screen right
        // now, plus anything already tracked. No narrower pre-filter — whether an absent
        // product's orders are genuinely gone or merely queued past the visible window is
        // exactly what Phase 3a resolves per order, in reconcileProduct.
        var touched = new LinkedHashSet<String>(byProduct.keySet());
        storage.stream().map(Order::productId).forEach(touched::add);

        // ── Phase 1 for every touched product, first ───────────────────────────
        // Must fully finish before Phase 2–4 (reconcileProduct) runs for any product.
        var matches = touched.stream()
                .map(id -> matchScreenEntries(id, storage, byProduct.getOrDefault(id, List.of()), origin))
                .toList();

        // Whole-screen slot map assembled from all products' Phase 1 outputs.
        var onScreenBySlotBuilder = new HashMap<Integer, Order>();
        for (var match : matches) {
            onScreenBySlotBuilder.putAll(match.reconciledBySlot());
        }
        Map<Integer, Order> onScreenBySlot = Map.copyOf(onScreenBySlotBuilder);

        // ── Phase 2–4 for every touched product ─────────────────────────────────
        var staged = matches.stream()
                .map(match -> reconcileProduct(match, onScreenBySlot, origin, key))
                .toList();

        commitAll(staged, origin, key);

        RenderedOrdersIndex.update(onScreenBySlot);
    }

    /**
     * Phase 1 for one product: matches this tick's screen entries against its stored
     * orders, synthesizing an order for any entry with no match. Pure computation —
     * touches neither storage nor the book.
     *
     * <p>Must complete for every touched product before {@link #reconcileProduct}
     * (Phase 2–4) runs for any of them — see {@link #onContainerLoaded}. Each product's
     * output is a complete, exact partition of its slots: one entry in, one matched-or-
     * synthesized order out. Nothing shown is uncounted; nothing absent is invented. That
     * completeness is what lets Phase 3a treat a slot claim outside this product's own
     * output as disproven without any further verification.
     *
     * <p>Synthesized orders for unmatched entries receive a {@code placedAt} via
     * {@link #resolvePlacedAt}: entries earlier in this product's slot-ascending listing
     * resolve to an older timestamp than later ones, preserving FIFO ordering among
     * orders synthesized in the same pass.
     */
    private ProductMatch matchScreenEntries(String productId, List<Order> storage, List<PageOrderParser.ParsedEntry> entries, BazaarDataOrigin.OrdersScreen origin) {
        // UUID-keyed for O(1) lookup and Maps.difference.
        Map<UUID, Order> storedById = storage.stream()
                .filter(order -> order.productId().equals(productId))
                .collect(Collectors.toMap(Order::id, order -> order));

        var usedIds = new HashSet<UUID>();
        var reconciledById = new LinkedHashMap<UUID, Order>();   // matched+synthesized (diff right)

        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var match = findMatch(storedById, usedIds, entry);

            if (match != null) {
                usedIds.add(match.id());
                var after = reconcileExisting(match, entry, origin);

                reconciledById.put(match.id(), after);

                if (!match.slotPosition().equals(after.slotPosition())) {
                    PlayerActionUtil.notifyAll("%s — Reanchored %s → %s: %s".formatted(
                            origin.describe(),
                            match.slotPosition().describe(), after.slotPosition().describe(),
                            after.describe()), NotificationType.ORDERDATA);
                }
            } else {
                // Entries earlier in this product's slot-ascending list get an older
                // placedAt than later ones, so same-pass synthesized siblings never tie.
                long placedAt = resolvePlacedAt(entry, origin, entries.size() - 1 - i);
                var synth = synthesizeNew(entry, productId, origin, placedAt);
                reconciledById.put(synth.id(), synth);

                PlayerActionUtil.notifyAll("%s — Synthesized untracked order: %s".formatted(origin.describe(), synth.describe()), NotificationType.ORDERDATA);
            }
        }

        return new ProductMatch(productId, storedById, reconciledById, indexBySlot(reconciledById));
    }

    /**
     * Phase 2–4 for one product: classifies unmatched stored orders, composes the book
     * mutation chain, and stages the result via {@link #stageCommit}.
     * Does not touch storage — see {@link #onContainerLoaded}, which stages every
     * touched product before any of them is committed.
     *
     * @param match          Phase 1 output for this product, produced by
     *                       {@link #matchScreenEntries}.
     * @param onScreenBySlot all products' Phase 1 outputs glued together by slot.
     *                       Consulted only after {@code match.reconciledBySlot()} has
     *                       already ruled out a same-product explanation — a hit here
     *                       always names a genuinely different product's order.
     * @param origin         timestamp and type of this screen observation.
     */
    private StagedCommit reconcileProduct(ProductMatch match, Map<Integer, Order> onScreenBySlot, BazaarDataOrigin.OrdersScreen origin, ProfileKey key) {        String productId = match.productId();
        Map<UUID, Order> storedById = match.storedById();
        Map<UUID, Order> reconciledById = match.reconciledById();
        Map<Integer, Order> reconciledBySlot = match.reconciledBySlot();

        // ── Phase 2: classify unmatched stored orders via Maps.difference ────
        //
        //   Left  = storedById     (before-state for this product)
        //   Right = reconciledById (after-state: matched + synthesized)
        //
        //   entriesOnlyOnLeft  → not matched by screen → Evict | preserved | off-screen | Reanchor (demoted, position unknown)
        //   entriesOnlyOnRight → synthesized (fresh UUID absent from storage) → Place
        //   entriesDiffering   → matched orders whose state advanced → Update | PriceCorrection | Reanchor
        //   entriesInCommon    → matched, unchanged — no delta
        var diff = Maps.difference(storedById, reconciledById);

        var preserved = new ArrayList<Order>();
        var offScreen  = new ArrayList<Order>();

        // Deltas produced this reconciliation — BookOnly goes in last (after chain is fully composed).
        var deltas = new ArrayList<OrderDelta>();

        // Book mutation chain — composed incrementally, sealed into BookOnly at the end.
        BookMutation<BazaarDataOrigin.UserPositionEvent> mutation = BookMutation.none();

        // ── Phase 3a: classify entriesOnlyOnLeft ─────────────────────────────
        for (var kv : diff.entriesOnlyOnLeft().entrySet()) {
            var order = kv.getValue();

            // Local before global: same-product Phase 1 output first, then the full
            // screen map. A hit on either names a genuinely different order — this one
            // failed Phase 1 matching.
            Order sibling = null, screenOccupant = null;
            if (order.slotPosition() instanceof OrderSlotPosition.OnScreen(int slot)) {
                sibling = reconciledBySlot.get(slot);
                if (sibling == null) screenOccupant = onScreenBySlot.get(slot);
            }

            boolean futureDated = order.lastUpdatedAt() > origin.observedAt();

            // A contradicted slot proves the cached position is wrong but not that the
            // order disappeared. Disappearance is always checked before any position-only
            // Reanchor — an order that is both contradicted and gone evicts, not repositions.
            if (futureDated) {
                if (sibling != null || screenOccupant != null) {
                    // Position is disproven but state was written after this scan's observedAt;
                    // trust the newer source for state. Reanchor to OffScreen without advancing
                    // lastUpdatedAt — Reanchor in deltas forces the write without firing an event.
                    var demoted = order.reanchored(new OrderSlotPosition.OffScreen(0));
                    deltas.add(new OrderDelta.Reanchor(order, demoted));

                    PlayerActionUtil.notifyAll("%s — Reanchored off-screen (position only — order is newer than this scan) — slot confirmed held by %s: %s".formatted(
                            origin.describe(), (sibling != null ? sibling : screenOccupant).describe(),
                            order.describe()), NotificationType.ORDERDATA);
                } else {
                    PlayerActionUtil.notifyAll("%s — Skipped — updated after observation window: %s".formatted(origin.describe(), order.describe()), NotificationType.ORDERDATA);

                    preserved.add(order);
                }

            } else if (!order.isVisible()) {
                PlayerActionUtil.notifyAll("%s — Skipped — off-screen unanchored: %s".formatted(origin.describe(), order.describe()), NotificationType.ORDERDATA);

                offScreen.add(order);

            } else if (!order.isTerminal() && (sibling != null || (origin.observedAt() - order.lastUpdatedAt()) > EVICTION_GRACE_MS)) {
                // Was order.isLive() — under the new, narrower definition that would
                // never let an already-Expired order reach this branch at all once it
                // disappears from screen, leaving it stuck "preserved unchanged"
                // forever instead of being evaluated for auto-claim/eviction like a
                // live order would be.
                // sibling != null is non-futureDated here — that case is handled above.
                // Phase 1 confirmed this slot belongs to another order of this product;
                // the grace window does not apply.
                if (order.isFilled()) {
                    // Auto-claim: order left screen in filled state, so THIS client
                    // never clicked claim on it. Two genuinely different explanations,
                    // neither about who's named on the order:
                    //   1. key's profile is known coop — any coop peer, regardless of
                    //      whose name is on this order, could have claimed it.
                    //   2. Enough real time passed since this order's own
                    //      lastUpdatedAt that a DIFFERENT SESSION of the same player —
                    //      solo profile, no coop involved at all — plausibly claimed
                    //      it while this client wasn't running.
                    // ambiguousClaimant() folds both into one check; neither depends on
                    // this order's own OrderAttribution, which answers a different
                    // question (who placed it, not who might have since acted on it).
                    var claimed = order.withClaim(order.unclaimedFilled(), origin);
                    deltas.add(OrderDelta.Update.claim(order, claimed));

                    PlayerActionUtil.notifyAll("%s — Auto-claimed %d units (left screen filled%s): %s".formatted(
                            origin.describe(), order.unclaimedFilled(),
                            sibling != null ? " — slot confirmed held by " + sibling.describe() : "",
                            order.describe()), NotificationType.ORDERDATA);
                } else if (order.isExpired()) {
                    deltas.add(new OrderDelta.Evict<>(order.cancelled(origin), BookMutation.none()));

                    PlayerActionUtil.notifyAll("%s — Expired order left screen (escrow recovered): %s".formatted(origin.describe(), order.describe()), NotificationType.ORDERDATA);
                } else {
                    // Cancelled eviction: decrement the book.
                    var cancelMutation = BookMutation.decrement(
                            TransactionType.of(order.side(), TransactionType.Method.ORDER),
                            order.pricePerItem(),
                            order.originalAmount() - order.claimedAmount(),
                            true);

                    mutation = mutation.then(cancelMutation);
                    deltas.add(new OrderDelta.Evict<>(order.cancelled(origin), cancelMutation));

                    PlayerActionUtil.notifyAll("%s — Cancelled — %s: %s".formatted(
                            origin.describe(),
                            sibling != null ? "slot confirmed held by a different order of this same product" : "disappeared from screen",
                            order.describe()), NotificationType.ORDERDATA);

                    PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (evicted)".formatted(
                                    origin.describe(),
                                    TransactionType.of(order.side(), TransactionType.Method.ORDER).getPriceType(),
                                    productId, order.originalAmount() - order.claimedAmount(), order.pricePerItem()),
                            NotificationType.BAZAARDATA);
                }
            } else if (screenOccupant != null) {
                // Contradicted by a different product's order but within the grace window.
                // Correct position only — if genuinely gone, the next reconciliation after
                // grace elapses will evict it.
                var demoted = order.reanchored(new OrderSlotPosition.OffScreen(0));
                deltas.add(new OrderDelta.Reanchor(order, demoted));

                PlayerActionUtil.notifyAll("%s — Reanchored off-screen (position only — inside grace window) — slot confirmed held by %s: %s".formatted(
                        origin.describe(), screenOccupant.describe(), order.describe()), NotificationType.ORDERDATA);
            } else {
                // Terminal or inside grace period, uncontradicted — retain without change.
                preserved.add(order);
            }
        }

        // ── Phase 3b: synthesized orders ─────────────────────────────────────
        for (var order : diff.entriesOnlyOnRight().values()) {
            deltas.add(new OrderDelta.Place<>(order, BookMutation.none()));
        }

        // entriesInCommon only — reconcileExisting returned found unchanged (same-reference
        // contract). Slot changes go as Reanchor deltas so the write is never silently dropped.
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
                deltas.add(new OrderDelta.PriceCorrection<>(before, after, repriceMutation));

                PlayerActionUtil.notifyAll("%s — Price corrected %.4f → %.4f: %s".formatted(
                        origin.describe(), before.pricePerItem(), after.pricePerItem(),
                        after.describe()), NotificationType.ORDERDATA);

                PlayerActionUtil.notifyAll("%s — Book re-priced: %s %s Δ%d @ %.4f → %.4f".formatted(
                                origin.describe(),
                                TransactionType.of(before.side(), TransactionType.Method.ORDER).getPriceType(),
                                productId, before.unfilledAmount(), before.pricePerItem(), after.pricePerItem()),
                        NotificationType.BAZAARDATA);
            } else if (after.filledAmount() > before.filledAmount()) {
                int fillDelta = after.filledAmount() - before.filledAmount();
                var fillMutation = BookMutation.decrement(
                        TransactionType.of(after.side(), TransactionType.Method.ORDER),
                        after.pricePerItem(), fillDelta, false);

                mutation = mutation.then(fillMutation);
                deltas.add(OrderDelta.Update.fill(before, after, fillMutation));

                PlayerActionUtil.notifyAll("%s — Fill advanced %d → %d (Δ%d): %s".formatted(
                        origin.describe(), before.filledAmount(), after.filledAmount(),
                        fillDelta, after.describe()), NotificationType.ORDERDATA);

                PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (fill advance)".formatted(
                                origin.describe(),
                                TransactionType.of(after.side(), TransactionType.Method.ORDER).getPriceType(),
                                productId, fillDelta, after.pricePerItem()),
                        NotificationType.BAZAARDATA);

            } else if (after.isExpired() && !before.isExpired()) {
                // Decrement the unfilled volume Hypixel removed from the book at expiry.
                // No-op if the API snapshot already cleared the level. after.unfilledAmount()
                // reflects reconciledFill so the amount is correct when fill also advanced
                // this tick.
                var expiryMutation = BookMutation.decrement(
                        TransactionType.of(after.side(), TransactionType.Method.ORDER),
                        after.pricePerItem(),
                        after.unfilledAmount(),
                        true);  // terminal = true (unfilled volume is permanently gone)

                mutation = mutation.then(expiryMutation);
                deltas.add(OrderDelta.Update.expiry(before, after, expiryMutation));

                PlayerActionUtil.notifyAll("%s — Expired (Δunfilled=%d @ %.4f decremented from book): %s".formatted(
                        origin.describe(), after.unfilledAmount(), after.pricePerItem(),
                        after.describe()), NotificationType.ORDERDATA);

                PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (order expired)".formatted(
                                origin.describe(),
                                TransactionType.of(after.side(), TransactionType.Method.ORDER).getPriceType(),
                                productId, after.unfilledAmount(), after.pricePerItem()),
                        NotificationType.BAZAARDATA);
            } else if (after.claimedAmount() > before.claimedAmount()) {
                // Claim advance with no concurrent fill change.
                deltas.add(OrderDelta.Update.claim(before, after));
            } else {
                // Screen position changed, no state mutation. Reanchor carries after
                // into the write without firing an event or book mutation.
                deltas.add(new OrderDelta.Reanchor(before, after));
            }
        }

        // ── Phase 3d: floor affirmation ─────────────────────────────────────────
        // Unconditional — a snapshot eviction between loads can drop a level with no
        // corresponding order-state change; floor affirmation restores it.
        for (var side : TransactionType.Side.values()) {
            var transaction = TransactionType.of(side, TransactionType.Method.ORDER);
            var levels = reconciledById.values().stream()
                    .filter(Order::isActive)
                    .filter(order -> order.side() == side && order.unfilledAmount() > 0)
                    .map(order -> new PriceLevel(order.pricePerItem(), order.unfilledAmount(), 1, origin))
                    .toList();

            mutation = mutation.then(BookMutation.floor(transaction, levels));
        }

        return stageCommit(productId, mutation, deltas, preserved, offScreen, unchanged, origin, key);
    }

    /**
     * Phase 1 output for one product: this tick's screen entries matched against (or
     * synthesized from) its stored orders, the stored map Phase 2 diffs against, and that
     * same reconciled state re-indexed by slot for Phase 3a's collision checks.
     *
     * <p>{@code reconciledBySlot} is immutable, and, within one product, provably
     * injective — see {@link #indexBySlot}.
     */
    private record ProductMatch(String productId, Map<UUID, Order> storedById, Map<UUID, Order> reconciledById, Map<Integer, Order> reconciledBySlot) {}

    /**
     * Indexes {@code reconciledById}'s on-screen orders by slot — a re-keying of exactly
     * the data {@code reconciledById} already holds, nothing more.
     *
     * <p>Provably a total, collision-free re-keying, not merely a defensively-coded one:
     * every value here traces back to exactly one {@link PageOrderParser.ParsedEntry}, and
     * {@link #matchScreenEntries} produces exactly one output order per input entry (one
     * {@code reconciledById.put} per loop iteration, unconditionally). Since
     * {@link PageOrderParser#parse} yields at most one entry per occupied container slot —
     * a slot cannot hold two items at once — no two entries here can ever collide. The
     * {@code LOG.warn} below is a tripwire, not a control path: it should never fire, and
     * if it ever does, that invariant broke somewhere upstream and deserves attention, not
     * silent data loss.
     */
    private static Map<Integer, Order> indexBySlot(Map<UUID, Order> reconciledById) {
        var bySlot = new HashMap<Integer, Order>();

        for (var order : reconciledById.values()) {
            if (!(order.slotPosition() instanceof OrderSlotPosition.OnScreen(int slot))) continue;
            bySlot.put(slot, order);
        }

        return Map.copyOf(bySlot);
    }

    /**
     * Finds the best stored order to match against a screen entry using a four-tier
     * preference: exact slot index → active orders → any live order → identity-only
     * coop fallback for a still-unpriced {@code CoopUnknown} order.
     *
     * <p>The first three tiers share the same base identity predicate (side +
     * originalAmount + similar price). Slot-index matching is preferred because it
     * anchors reconciliation to the screen's authoritative positional data; the
     * active/live fallbacks handle reanchoring after a slot shift. The fourth tier
     * drops the price check — see its own comment below for why that's safe only for
     * {@code CoopUnknown}.
     *
     * <p>Within a tier, multiple identity-matching candidates are broken oldest-{@code placedAt}
     * first. Callers process {@code entries} in slot-ascending order, and per
     * {@link OrdersPageLayout} a lower slot corresponds to an older order within a same-price
     * group, so this greedily reconstructs the correct entry-to-order correspondence across
     * passes instead of picking arbitrarily among same-identity siblings.
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

        Comparator<Order> oldestFirst = Comparator.comparingLong(Order::placedAt);

        return candidates.values().stream()
                .filter(base.and(order -> order.slotPosition().isOnScreenAt(entry.item().slotIndex())))
                .min(oldestFirst)
                .or(() -> candidates.values().stream().filter(base.and(Order::isActive)).min(oldestFirst))
                .or(() -> candidates.values().stream().filter(base).min(oldestFirst))
                // Coop fallback: the stored order's price is unknown (0.0) because it was
                // synthesized from a fill broadcast with no placement ever seen — see
                // OrderFilledDataSource. Match on volume + side + CoopUnknown alone, not
                // isCoopContext() — a CoopSelf or CoopPeer order is always screen-sourced
                // and so always has a real price, and is matched by an earlier tier
                // already, on that price. A hit here is the exact moment a CoopUnknown
                // attribution reconciles into whichever this screen confirms — CoopSelf if
                // it's this same account from an untracked session, CoopPeer if it's
                // genuinely someone else — in reconcileExisting below.
                .or(() -> candidates.values().stream()
                        .filter(order -> !usedIds.contains(order.id())
                                && order.attribution().isCoopContext()
                                && order.side() == info.getTransaction().getSide()
                                && order.originalAmount() == info.getVolume()
                                && order.isFilled())
                        .min(oldestFirst))
                .orElse(null);
    }

    /**
     * Advances a matched order's state from its screen entry, returning the original
     * reference when no field changed.
     *
     * <p><b>Same-reference contract:</b> when every reconciled field equals the stored
     * field, {@code found} is returned unchanged so that {@link Maps#difference}
     * classifies it as {@code entriesInCommon}.
     *
     * <p><b>Fill:</b> advanced to {@code max(storedFill, screenFill)}, capped at
     * {@code originalAmount}. The screen value is never used to regress fill.
     *
     * <p><b>Claim noise:</b> when {@code claimableAmount} is at or above
     * {@link PageOrderParser#CLAIM_TRUNCATION_THRESHOLD} it is k/M-abbreviated and the
     * derived claimed count is untrustworthy — the stored value is preserved. Below the
     * threshold, phantom claimed units that arise from a k/M-abbreviated fill count are
     * suppressed before the screen-derived value is accepted.
     *
     * <p><b>Status:</b> derived from the reconciled fill and the screen's own expired
     * flag, with one carry-over: an already-known {@link OrderStatus.Expired} timestamp
     * is kept rather than restamped, so a more precise earlier detection is never
     * overwritten by a coarser later one.
     *
     * <p><b>Price and expiry:</b> the stored price is replaced with the screen's exact
     * fractional value once matched, but only for orders whose total value crossed
     * {@link OrderInfo#FOLDING_THRESHOLD} — below that, source's own price was never
     * imprecise to begin with. {@code expiresAt} prefers the lore-derived stamp over
     * the stored one whenever the screen supplies one.
     */
    private static Order reconcileExisting(Order found, PageOrderParser.ParsedEntry entry, BazaarDataOrigin.OrdersScreen origin) {
        int screenFill = Math.min(entry.filledAmount(), found.originalAmount());

        // Always advance to max(stored, screen), capped at originalAmount.
        int reconciledFill = screenFill >= found.originalAmount()
                ? found.originalAmount()
                : Math.max(found.filledAmount(), screenFill);

        boolean claimableTrustworthy = entry.claimableAmount() < PageOrderParser.CLAIM_TRUNCATION_THRESHOLD;
        boolean fillAbbreviated = screenFill >= PageOrderParser.FILL_TRUNCATION_THRESHOLD && screenFill < found.originalAmount();

        int reconciledClaimed;
        if (!claimableTrustworthy) {
            reconciledClaimed = Math.min(found.claimedAmount(), reconciledFill);
        } else {
            int screenClaimed = entry.claimedAmount();
            boolean suppressNoise = fillAbbreviated
                    && screenClaimed > found.claimedAmount()
                    && (screenClaimed - found.claimedAmount()) < PageOrderParser.FILL_TRUNCATION_MAX;

            reconciledClaimed = suppressNoise
                    ? found.claimedAmount()
                    : Math.max(found.claimedAmount(), screenClaimed);
        }
        reconciledClaimed = Math.min(reconciledClaimed, reconciledFill);

        // Reconciles the NonTerminal position first — from reconciledFill alone,
        // independent of whether entry.expired() also fires this tick — then wraps
        // it in Expired only if needed. found.status().effectiveNonTerminal() looks
        // through an already-Expired found the same way a bare NonTerminal found
        // does, so "was this order already expired last tick" no longer needs a
        // separate carry-through branch the way it used to.
        // Target shape decided by PageOrderParser.resolveNonTerminal, fed the
        // RECONCILED fill (already never-regressed) — not entry.observedStatus(),
        // which was derived from the RAW screen fill and could momentarily
        // under-report relative to what's already known. Within whichever shape
        // that implies, reuse the PRIOR instance when the prior was already that
        // same shape — preserving Filled's original filledAt, Partial's
        // firstFilledAt, and Set's reference identity for the no-change case —
        // constructing fresh only when the shape genuinely changed.
        var priorNonTerminal = found.status().effectiveNonTerminal();
        var targetShape = PageOrderParser.resolveNonTerminal(reconciledFill, entry.info().getVolume(), origin.observedAt());
        OrderStatus.NonTerminal reconciledNonTerminal = switch (targetShape) {
            case OrderStatus.Filled fresh -> priorNonTerminal.filter(ns -> ns instanceof OrderStatus.Filled).orElse(fresh);
            case OrderStatus.Partial fresh -> priorNonTerminal.filter(ns -> ns instanceof OrderStatus.Partial)
                    .map(OrderStatus.Partial.class::cast)
                    .map(existing -> reconciledFill > found.filledAmount()
                            ? new OrderStatus.Partial(existing.firstFilledAt(), origin.observedAt())
                            : existing)
                    .orElse(fresh);
            case OrderStatus.Set fresh -> priorNonTerminal.filter(ns -> ns instanceof OrderStatus.Set).orElse(fresh);
        };
        OrderStatus reconciledStatus;
        if (entry.expired()) {
            // Preserve the existing Expired timestamp when already known, to avoid
            // overwriting a more precise earlier detection — but let the WRAPPED
            // NonTerminal still track reconciledNonTerminal above, since that's
            // ordinary fill/claim-state bookkeeping, not the expiry detection itself.
            long expiredAt = found.status() instanceof OrderStatus.Expired existing ? existing.expiredAt() : origin.observedAt();
            reconciledStatus = new OrderStatus.Expired(expiredAt, reconciledNonTerminal);
        } else {
            reconciledStatus = reconciledNonTerminal;
        }

        // expiresAt reconciliation — prefer lore-derived stamp; fall back to stored value.
        // Never replace a more precise lore stamp with a coarser stored one.
        Long reconciledExpiresAt = (entry.expiresAt() != null)
                ? entry.expiresAt()
                : found.expiresAt();

        // Attribution only ever upgrades — CoopUnknown -> whatever this screen read
        // confirms — never downgrades an already-confirmed Self or CoopPeer based on
        // a later, weaker signal.
        OrderAttribution reconciledAttribution = found.attribution() instanceof OrderAttribution.CoopUnknown
                ? entry.attribution()
                : found.attribution();

        // Same-reference return — Maps.difference entriesInCommon relies on this.
        if (found.slotPosition().isOnScreenAt(entry.item().slotIndex())
                && found.filledAmount() == reconciledFill
                && found.claimedAmount() == reconciledClaimed
                && found.status() == reconciledStatus
                && Double.compare(found.pricePerItem(), entry.info().getPricePerItem()) == 0
                && Objects.equals(found.expiresAt(), reconciledExpiresAt)
                && found.attribution().equals(reconciledAttribution)) {
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
                found.placedAt(), origin.observedAt(), reconciledAttribution,
                reconciledExpiresAt);
    }

    /**
     * Constructs a tracked {@link Order} from a screen entry with no prior storage match.
     *
     * <p>{@code placedAt} is supplied by the caller via {@link #resolvePlacedAt}: reversed
     * from the lore-derived {@code expiresAt} when available, otherwise a staggered estimate
     * from {@code observedAt} that preserves FIFO ordering among same-pass siblings.
     * {@code expiresAt} is the lore-derived stamp when present; otherwise {@code null},
     * resolved on demand via {@link Order#effectiveExpiresAt()}.
     */
    private static Order synthesizeNew(PageOrderParser.ParsedEntry entry, String productId, BazaarDataOrigin.OrdersScreen origin, long placedAt) {
        var info = entry.info();

        return new Order(
                UUID.randomUUID(), productId,
                info.getTransaction().getSide(),
                info.getPricePerItem(),
                info.getVolume(),
                entry.filledAmount(), entry.claimedAmount(),
                new OrderSlotPosition.OnScreen(entry.item().slotIndex()),
                entry.observedStatus(),
                placedAt, origin.observedAt(), entry.attribution(),
                entry.expiresAt());
    }

    /**
     * Best-effort {@code placedAt} for a synthesized order with no prior storage match.
     *
     * <p>Prefers reversing the lore-derived {@code expiresAt} against
     * {@link PageOrderParser#ORDER_EXPIRY_MS} — anchored to the true placement instant
     * and accurate regardless of order age. Only populated within the final ~48 hours
     * before expiry (see {@link PageOrderParser}).
     *
     * <p>Falls back to {@code origin.observedAt()} minus {@code staggerOffset}: a small
     * caller-supplied decrement that makes entries earlier in this product's slot-ascending
     * screen listing resolve to an older {@code placedAt} than later ones, preserving FIFO
     * ordering among orders synthesized in the same pass instead of collapsing them onto a
     * single identical timestamp.
     */
    private static long resolvePlacedAt(PageOrderParser.ParsedEntry entry, BazaarDataOrigin.OrdersScreen origin, long staggerOffset) {
        long base = entry.expiresAt() != null
                ? entry.expiresAt() - PageOrderParser.ORDER_EXPIRY_MS
                : origin.observedAt();

        return base - staggerOffset;
    }

    /**
     * One product's contribution to a batched {@link #commitAll} write, produced by
     * {@link #stageCommit}: the orders it contributes to the combined order list, and the
     * events to fire once that write lands. {@code changed} governs whether
     * {@link BazaarDataUpdateEvent} is posted for this product.
     */
    private record StagedCommit(String productId, List<Order> orders, List<UserOrderEvent> events, boolean changed) {}

    /**
     * Resolves one product's book mutation and order deltas into a {@link StagedCommit}.
     * Does not touch {@link UserOrdersStorage} — every product touched by a screen load
     * must be staged before any of them reaches storage; see {@link #commitAll}.
     *
     * <p>The book mutation is applied immediately, not deferred: {@link com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry}
     * books are keyed per product, independent of the tracked-order list, so unlike the
     * order list itself they carry no cross-product invariant and need no batching.
     *
     * @param deltas    fully-resolved order-state deltas for this product — Place, Evict,
     *                  Update, and PriceCorrection entries only. BookOnly and None never
     *                  appear here; book mutation is carried by the {@code mutation} parameter.
     * @param preserved orders retained unchanged (grace window / terminal / post-obs).
     * @param offScreen orders retained unchanged (unanchored, no confirmed slot).
     * @param unchanged in-common (state-identical) orders — carried through with no event.
     *                  Pure slot-reanchor orders — both a confirmed reposition and a
     *                  disproven-claim demotion to {@link OrderSlotPosition.OffScreen} —
     *                  arrive as {@link OrderDelta.Reanchor} entries in {@code deltas}
     *                  instead, so their updated {@link OrderSlotPosition} is still
     *                  captured even though they fire no event.
     * @param mutation  pre-composed book mutation for this product — applied here.
     */
    private StagedCommit stageCommit(
            String productId,
            BookMutation<BazaarDataOrigin.UserPositionEvent> mutation,
            List<OrderDelta> deltas,
            List<Order> preserved,
            List<Order> offScreen,
            List<Order> unchanged,
            BazaarDataOrigin.OrdersScreen origin,
            ProfileKey key) {

        boolean bookChanged = mutation.apply(productId, origin);

        var next = new ArrayList<Order>(preserved.size() + offScreen.size() + unchanged.size() + deltas.size());
        next.addAll(preserved);
        next.addAll(offScreen);
        next.addAll(unchanged);

        var events = new ArrayList<UserOrderEvent>();

        for (var delta : deltas) {
            switch (delta) {
                case OrderDelta.Place place -> {
                    next.add(place.order());
                    events.add(new UserOrderEvent.Placed(place.order(), key));
                }
                case OrderDelta.Evict eviction -> {
                    next.add(eviction.order());
                    events.add(new UserOrderEvent.Cancelled(eviction.order(), key));
                }
                case OrderDelta.Update update -> {
                    next.add(update.after());
                    events.addAll(getMutationEvents(update.before(), update.after(), key));
                }
                case OrderDelta.PriceCorrection correction -> {
                    next.add(correction.after());
                    events.addAll(getMutationEvents(correction.before(), correction.after(), key));
                }
                case OrderDelta.Reanchor reanchor -> next.add(reanchor.after());
                default -> {}
            }
        }

        if (!deltas.isEmpty() && NotificationType.ORDERDATA.isEnabled()) {
            long placed = deltas.stream().filter(it -> it instanceof OrderDelta.Place).count();
            long evicted = deltas.stream().filter(it -> it instanceof OrderDelta.Evict).count();
            long updated = deltas.stream().filter(it -> it instanceof OrderDelta.Update).count();
            long expired = deltas.stream().filter(it -> it instanceof OrderDelta.Update u && u.kind() == OrderDelta.Update.UpdateKind.EXPIRY).count();
            long priceCorrected = deltas.stream().filter(it -> it instanceof OrderDelta.PriceCorrection).count();
            long reanchored = deltas.stream().filter(it -> it instanceof OrderDelta.Reanchor).count();

            PlayerActionUtil.notifyAll("%s — %s staged: Δ%d placed, Δ%d evicted, Δ%d updated, Δ%d expired, Δ%d price-corrected, Δ%d reanchored".formatted(
                    origin.describe(), productId, placed, evicted, updated, expired, priceCorrected, reanchored), NotificationType.ORDERDATA);
        }

        return new StagedCommit(productId, next, events, !deltas.isEmpty() || bookChanged);
    }

    /**
     * Writes every {@link #stageCommit} result from one screen load in a single atomic
     * storage write, then fires each product's events and posts
     * {@link BazaarDataUpdateEvent} for every product where {@link StagedCommit#changed()}
     * is {@code true}. No-ops entirely if nothing changed for any product.
     *
     * <p>The write has to be batched, not just deferred. {@link UserOrdersStorage#apply}
     * indexes live orders by container slot — every live order's {@link OrderSlotPosition}
     * must be unique — over the <em>entire</em> tracked-order list, on every call. A
     * product's own Phase 3a (see {@link #reconcileProduct}) is what guarantees no two
     * orders can still collide by the time they get here — but that guarantee is only as
     * good as the picture Phase 3a resolved against. Writing one product's corrected list
     * before another product's own correction had itself been produced would reopen
     * exactly this gap. Waiting for every product to finish, then folding their combined
     * result into one write, removes that gap entirely: every order reaching storage
     * already passed through its own owning product's Phase 3a.
     */
    private void commitAll(List<StagedCommit> staged, BazaarDataOrigin.OrdersScreen origin, ProfileKey key) {
        if (staged.stream().noneMatch(StagedCommit::changed)) return;

        var next = new ArrayList<Order>(staged.stream().mapToInt(commit -> commit.orders().size()).sum());
        staged.forEach(commit -> next.addAll(commit.orders()));

        var touchedProducts = staged.stream()
                .filter(StagedCommit::changed)
                .map(StagedCommit::productId)
                .collect(Collectors.toUnmodifiableSet());

        FillInference.settle(key, touchedProducts, origin, ignored -> next);
        staged.forEach(commit -> commit.events().forEach(event -> event.post(BazaarUtils.EVENT_BUS)));

        staged.stream()
                .filter(StagedCommit::changed)
                .forEach(commit -> new BazaarDataUpdateEvent(commit.productId(), origin).post(BazaarUtils.EVENT_BUS));
    }

    /**
     * Recovers the {@link UserOrderEvent}s implied by one order's before/after diff.
     * Detection stays field-comparison-based — {@link OrderDelta.PriceCorrection}
     * shares this method and carries no {@link OrderDelta.Update.UpdateKind} to
     * read — but event CONSTRUCTION now delegates to
     * {@link OrderDelta.Update.UpdateKind#getEvent}, the same lookup
     * {@code OrderDelta.Update} itself is built on, instead of reimplementing it.
     */
    private static List<UserOrderEvent> getMutationEvents(Order before, Order after, ProfileKey key) {
        var result = new ArrayList<UserOrderEvent>();
        if (after.isExpired() && !before.isExpired()) {
            result.add(OrderDelta.Update.UpdateKind.EXPIRY.getEvent(before, after, key));

            return result;
        }

        // effectiveNonTerminal(), not a direct instanceof — an order can be ALREADY
        // Expired this tick and last tick alike (the EXPIRY branch above only fires
        // on the transition), and still have genuinely advanced its fill on THIS
        // tick's screen read. A direct instanceof against the outer (possibly
        // Expired-wrapped) status would silently miss that advance entirely.
        var afterFill = after.status().effectiveNonTerminal();
        var beforeFill = before.status().effectiveNonTerminal();

        if (afterFill.filter(ns -> ns instanceof OrderStatus.Filled).isPresent()
                && beforeFill.filter(ns -> ns instanceof OrderStatus.Filled).isEmpty()) {
            result.add(OrderDelta.Update.UpdateKind.FILL.getEvent(before, after, key));
        } else if (afterFill.filter(ns -> ns instanceof OrderStatus.Partial).isPresent()) {
            if (beforeFill.filter(ns -> ns instanceof OrderStatus.Partial).isEmpty() || after.filledAmount() > before.filledAmount()) {
                result.add(OrderDelta.Update.UpdateKind.FILL.getEvent(before, after, key));
            }
        }

        if (after.claimedAmount() > before.claimedAmount()) {
            result.add(OrderDelta.Update.UpdateKind.CLAIM.getEvent(before, after, key));
        }

        return result;
    }
}