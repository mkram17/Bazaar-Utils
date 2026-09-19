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
import java.util.stream.Stream;

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
 * {@code entriesDiffering} → {@link OrderDelta.Update}, {@link OrderDelta.DataCorrection},
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

    /**
     * Reconciles every product's tracked orders against this tick's Orders page render.
     *
     * <p>Every touched product runs Phase 1 ({@link #matchScreenEntries}) before any of
     * them runs Phase 2–4 ({@link #reconcileProduct}). Every product is staged via
     * {@link #stageCommit} before {@link #commitAll} commits the whole tick in one write
     * — see that method for why the write must be batched. Once storage has committed,
     * {@link RenderedOrdersIndex#update} is stamped with this tick's whole-screen slot
     * map, the same map Phase 2–4 consulted for cross-product collision checks.
     */
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

        long unparsedOccupied = items.stream()
                .filter(item -> !item.isEmpty())
                .filter(item -> OrdersPageLayout.isOrderSlot(item.slotIndex(), event.getContainerSlots().size()))
                .count() - parsed.size();

        int totalBuy = (int) parsed.stream().filter(entry -> entry.info().getTransaction().getSide() == TransactionType.Side.BUY).count();
        int totalSell = parsed.size() - totalBuy;

        var exhaustive = unparsedOccupied > 0
                ? new ExhaustiveSides(false, false)
                : new ExhaustiveSides(
                OrdersPageLayout.isSideExhaustive(TransactionType.Side.BUY, totalBuy, totalSell),
                OrdersPageLayout.isSideExhaustive(TransactionType.Side.SELL, totalSell, totalBuy));

        Map<String, List<PageOrderParser.ParsedEntry>> byProduct = new LinkedHashMap<>();
        parsed.forEach(entry -> byProduct
                .computeIfAbsent(entry.info().getProductId(), k -> new ArrayList<>())
                .add(entry));

        Map<String, List<Order>> storageByProduct = storage.stream()
                .collect(Collectors.groupingBy(Order::productId));

        // Every product that could need reconciling this tick: anything on screen right
        // now, plus anything already tracked. No narrower pre-filter — whether an absent
        // product's orders are genuinely gone or merely queued past the visible window is
        // exactly what Phase 3a resolves per order, in reconcileProduct.
        var touched = new LinkedHashSet<>(byProduct.keySet());
        touched.addAll(storageByProduct.keySet());

        // ── Phase 1 for every touched product, first ───────────────────────────
        // Must fully finish before Phase 2–4 (reconcileProduct) runs for any product.
        var matches = touched.stream()
                .map(id -> matchScreenEntries(id, storageByProduct.getOrDefault(id, List.of()), byProduct.getOrDefault(id, List.of()), origin))
                .toList();

        // Whole-screen slot map assembled from all products' Phase 1 outputs.
        var onScreenBySlotBuilder = new HashMap<Integer, Order>();
        for (var match : matches) {
            onScreenBySlotBuilder.putAll(match.reconciledBySlot());
        }
        Map<Integer, Order> onScreenBySlot = Map.copyOf(onScreenBySlotBuilder);

        // ── Phase 2–4 for every touched product ─────────────────────────────────
        var staged = matches.stream()
                .map(match -> reconcileProduct(match, onScreenBySlot, origin, key, exhaustive))
                .toList();

        Set<UUID> observed = matches.stream()
                .flatMap(match -> match.reconciledById().keySet().stream())
                .collect(Collectors.toUnmodifiableSet());

        commitAll(staged, origin, key, observed);

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
     * {@link PageOrderParser.ParsedEntry#resolvePlacedAt}: entries earlier in this product's slot-ascending listing
     * resolve to an older timestamp than later ones, preserving FIFO ordering among
     * orders synthesized in the same pass.
     */
    private ProductMatch matchScreenEntries(String productId, List<Order> storage, List<PageOrderParser.ParsedEntry> entries, BazaarDataOrigin.OrdersScreen origin) {
        // UUID-keyed for O(1) lookup and Maps.difference.
        Map<UUID, Order> storedById = storage.stream()
                .collect(Collectors.toMap(Order::id, order -> order));

        var ordered = entries.stream()
                .sorted(Comparator.comparingInt(entry -> entry.item().slotIndex()))
                .toList();

        var usedIds = new HashSet<UUID>();
        var reconciledById = new LinkedHashMap<UUID, Order>();   // matched+synthesized (diff right)

        for (int i = 0; i < ordered.size(); i++) {
            var entry = ordered.get(i);
            var match = findMatch(storedById, usedIds, entry);
            Order result;

            if (match != null) {
                usedIds.add(match.id());
                result = reconcileExisting(match, entry, origin);
                reconciledById.put(match.id(), result);

                if (!match.slotPosition().equals(result.slotPosition())) {
                    PlayerActionUtil.notifyAll("%s — Reanchored %s → %s: %s".formatted(
                            origin.describe(),
                            match.slotPosition().describe(), result.slotPosition().describe(),
                            result.describe()), NotificationType.ORDERDATA);
                }
            } else {
                long placedAt = entry.resolvePlacedAt(origin, ordered.size() - 1 - i);
                result = synthesizeNew(entry, productId, origin, placedAt);
                reconciledById.put(result.id(), result);

                PlayerActionUtil.notifyAll("%s — Synthesized untracked order: %s".formatted(origin.describe(), result.describe()), NotificationType.ORDERDATA);
            }
        }

        return new ProductMatch(productId, storedById, reconciledById, indexBySlot(reconciledById));
    }

    /** Whether this render proves each side complete — see {@link OrdersPageLayout#isSideExhaustive}. */
    private record ExhaustiveSides(boolean buy, boolean sell) {
        boolean forSide(TransactionType.Side side) {
            return side == TransactionType.Side.BUY ? buy : sell;
        }
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
     * @param key            the profile this reconciliation belongs to
     */
    private StagedCommit reconcileProduct(ProductMatch match, Map<Integer, Order> onScreenBySlot, BazaarDataOrigin.OrdersScreen origin, ProfileKey key, ExhaustiveSides exhaustive) {
        String productId = match.productId();
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
        //   entriesDiffering   → matched orders whose state advanced → Update | DataCorrection | Reanchor
        //   entriesInCommon    → matched, unchanged — no delta
        var diff = Maps.difference(storedById, reconciledById);

        var preserved = new ArrayList<Order>();
        var offScreen = new ArrayList<Order>();

        // Deltas produced this reconciliation. None of these carry a real book
        // mutation per this is a stale render of the book, we only floor levels
        // to this joined report.
        var deltas = new ArrayList<OrderDelta<BazaarDataOrigin.OrdersScreen>>();

        BookMutation<BazaarDataOrigin.OrdersScreen> mutation = BookMutation.none();

        // ── Phase 3a: classify entriesOnlyOnLeft ─────────────────────────────
        for (var kv : diff.entriesOnlyOnLeft().entrySet()) {
            var order = kv.getValue();

            if (order.isTerminal() && (origin.observedAt() - order.lastUpdatedAt()) < EVICTION_GRACE_MS) {
                continue;
            }

            // Local before global: same-product Phase 1 output first, then the
            // full screen map. A hit on either names a genuinely different
            // order — this one failed Phase 1 matching.
            Order sibling = null, screenOccupant = null;
            if (order.slotPosition() instanceof OrderSlotPosition.OnScreen(int slot)) {
                sibling = reconciledBySlot.get(slot);
                if (sibling == null) screenOccupant = onScreenBySlot.get(slot);
            }

            if (!order.isVisible() && !exhaustive.forSide(order.side())) {
                // Off-screen, and this read doesn't cover enough of the side
                // for continued absence to mean anything either way.
                PlayerActionUtil.notifyAll("%s — Skipped — off-screen unanchored: %s".formatted(origin.describe(), order.describe()), NotificationType.ORDERDATA);

                offScreen.add(order);
            } else if (sibling != null || (origin.observedAt() - order.lastUpdatedAt()) > EVICTION_GRACE_MS) {
                // Genuinely gone: either a same-product order provably occupies
                // where this one claimed to sit, or enough time has passed
                // since its own last update that continued absence isn't just
                // render lag.
                Order settled = order.filledAmount() > order.claimedAmount()
                        ? order.withClaim(order.unclaimedFilled(), origin)
                        : order;

                if (order.isFilled()) {
                    // Auto-claim: order left screen in filled state, so THIS
                    // client never clicked claim on it. Two genuinely
                    // different explanations, neither about who's named on
                    // the order:
                    //   1. key's profile is known coop — any coop peer could
                    //      have claimed it.
                    //   2. Enough real time passed since this order's own
                    //      lastUpdatedAt that a DIFFERENT SESSION of the same
                    //      player — solo, no coop involved at all — plausibly
                    //      claimed it while this client wasn't running.
                    deltas.add(OrderDelta.Update.claim(order, settled));

                    PlayerActionUtil.notifyAll("%s — Auto-claimed %d units (left screen filled%s): %s".formatted(
                            origin.describe(), order.unclaimedFilled(),
                            sibling != null ? " — slot confirmed held by " + sibling.describe() : "",
                            order.describe()), NotificationType.ORDERDATA);
                } else {
                    // Cancelled eviction.
                    var transaction = TransactionType.of(order.side(), TransactionType.Method.ORDER);

                    BookMutation<BazaarDataOrigin.OrdersScreen> cancelMutation = BookMutation.withdrawn(
                            transaction, order.pricePerItem(), order.unfilledAmount());

                    mutation = mutation.then(cancelMutation);
                    deltas.add(new OrderDelta.Evict<>(order, settled.cancelled(origin), cancelMutation));

                    PlayerActionUtil.notifyAll("%s — Cancelled — %s%s: %s".formatted(
                            origin.describe(),
                            sibling != null ? "slot confirmed held by a different order of this same product" : "disappeared from screen",
                            settled.claimedAmount() > order.claimedAmount() ? " (" + order.unclaimedFilled() + " filled units claimed with it)" : "",
                            order.describe()), NotificationType.ORDERDATA);

                    PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (evicted)".formatted(
                                    origin.describe(),
                                    transaction.getPriceType(),
                                    productId, order.originalAmount() - order.claimedAmount(), order.pricePerItem()),
                            NotificationType.BAZAARDATA);
                }
            } else if (screenOccupant != null) {
                // Contradicted by a different product's order but within the
                // grace window. Correct position only — if genuinely gone,
                // the next reconciliation after grace elapses will evict it.
                var demoted = order.reanchored(new OrderSlotPosition.OffScreen(0));
                deltas.add(new OrderDelta.Reanchor<>(order, demoted));

                PlayerActionUtil.notifyAll("%s — Reanchored off-screen (position only — inside grace window) — slot confirmed held by %s: %s".formatted(
                        origin.describe(), screenOccupant.describe(), order.describe()), NotificationType.ORDERDATA);
            } else {
                // Inside grace, uncontradicted — retain without change.
                preserved.add(order);
            }
        }

        // ── Phase 3b: synthesized orders ─────────────────────────────────────
        for (var order : diff.entriesOnlyOnRight().values()) {
            deltas.add(new OrderDelta.Place<>(order, BookMutation.none()));
        }

        // entriesInCommon only — reconcileExisting returned found unchanged
        // (same-reference contract). Slot changes, and placedAt/expiresAt
        // nudges within isSameInstant's tolerance, go as Reanchor deltas below
        // instead, so the write is never silently dropped.
        var unchanged = new ArrayList<>(diff.entriesInCommon().values());

        // ── Phase 3c: differing matched orders ───────────────────────────────
        for (var entry : diff.entriesDiffering().entrySet()) {
            var before = entry.getValue().leftValue();
            var after = entry.getValue().rightValue();

            boolean freshlyExpired = after.hasExpired() && !before.hasExpired();
            boolean filledMore = after.filledAmount() > before.filledAmount();
            boolean claimedMore = after.claimedAmount() > before.claimedAmount();
            boolean pricedDifferently = Double.compare(before.pricePerItem(), after.pricePerItem()) != 0;
            boolean priceSettled = before.priceExact() != after.priceExact() || pricedDifferently;
            boolean attributionResolved = !before.attribution().equals(after.attribution());

            var transaction = TransactionType.of(after.side(), TransactionType.Method.ORDER);

            if (freshlyExpired) {
                BookMutation<BazaarDataOrigin.OrdersScreen> expiryMutation = BookMutation.withdrawn(
                        transaction, after.pricePerItem(), after.unfilledAmount());

                mutation = mutation.then(expiryMutation);
                deltas.add(OrderDelta.Update.expiry(before, after, expiryMutation));

                PlayerActionUtil.notifyAll("%s — Expired (Δunfilled=%d @ %.4f decremented from book): %s".formatted(
                        origin.describe(), after.unfilledAmount(), after.pricePerItem(),
                        after.describe()), NotificationType.ORDERDATA);

                PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (order expired)".formatted(
                                origin.describe(),
                                transaction.getPriceType(),
                                productId, after.unfilledAmount(), after.pricePerItem()),
                        NotificationType.BAZAARDATA);
            } else if (filledMore) {
                int filledDelta = after.filledAmount() - before.filledAmount();

                BookMutation<BazaarDataOrigin.OrdersScreen> fillMutation = BookMutation.filled(
                        transaction, after.pricePerItem(), filledDelta, after.isFilled());

                mutation = mutation.then(fillMutation);
                deltas.add(OrderDelta.Update.fill(before, after, fillMutation));

                PlayerActionUtil.notifyAll("%s — Fill advanced %d → %d (Δ%d): %s".formatted(
                        origin.describe(), before.filledAmount(), after.filledAmount(),
                        filledDelta, after.describe()), NotificationType.ORDERDATA);

                PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (fill advance)".formatted(
                                origin.describe(),
                                transaction.getPriceType(),
                                productId, filledDelta, after.pricePerItem()),
                        NotificationType.BAZAARDATA);
            } else if (claimedMore) {
                var claimedDelta = after.claimedAmount() - before.claimedAmount();

                deltas.add(OrderDelta.Update.claim(before, after));

                PlayerActionUtil.notifyAll("%s — Claim advanced %d → %d (Δ%d): %s".formatted(
                        origin.describe(), before.claimedAmount(), after.claimedAmount(),
                        claimedDelta, after.describe()), NotificationType.ORDERDATA);
            } else if (priceSettled || attributionResolved) {
                BookMutation<BazaarDataOrigin.OrdersScreen> repriceMutation =
                        pricedDifferently
                                ? BookMutation.withdrawn(transaction, before.pricePerItem(), before.unfilledAmount())
                                : BookMutation.none();

                mutation = mutation.then(repriceMutation);
                deltas.add(new OrderDelta.DataCorrection<>(before, after, repriceMutation));

                if (priceSettled) {
                    PlayerActionUtil.notifyAll("%s — Price settled %.4f → %.4f (exact=%b): %s".formatted(
                            origin.describe(), before.pricePerItem(), after.pricePerItem(), after.priceExact(),
                            after.describe()), NotificationType.ORDERDATA);

                    PlayerActionUtil.notifyAll("%s — Book re-priced: %s %s Δ%d @ %.4f → %.4f".formatted(
                                    origin.describe(),
                                    transaction.getPriceType(),
                                    productId, before.unfilledAmount(), before.pricePerItem(), after.pricePerItem()),
                            NotificationType.BAZAARDATA);
                }
            } else {
                // Only the slot moved, or placedAt/expiresAt nudged within
                // isSameInstant's tolerance alongside it — neither is a fact
                // reconcileExisting's own guard treats as meaningful on its
                // own. Reanchor carries after into the write without firing
                // an event.
                deltas.add(new OrderDelta.Reanchor<>(before, after));
            }
        }

        // ── Phase 3d: floor affirmation
        var open = Stream.of(reconciledById.values(), preserved, offScreen)
                .flatMap(Collection::stream)
                .filter(Order::isOpen)
                .toList();

        for (var side : TransactionType.Side.values()) {
            var transaction = TransactionType.of(side, TransactionType.Method.ORDER);
            var levels = open.stream()
                    .filter(order -> order.side() == side && order.unfilledAmount() > 0)
                    .collect(Collectors.groupingBy(Order::pricePerItem))
                    .entrySet().stream()
                    .<PriceLevel<BazaarDataOrigin.UserPositionEvent>>map(e -> new PriceLevel<>(
                            e.getKey(),
                            e.getValue().stream().mapToInt(Order::unfilledAmount).sum(),
                            e.getValue().size(),
                            origin))
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
     * Finds the best stored order to match against a screen entry using a three-tier
     * preference: actionable orders matching identity → any identity-matching order
     * regardless of state → identity-only coop fallback for a still-unpriced
     * {@code CoopUnknown} order.
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
        double price = info.getPricePerItem();

        Predicate<Order> exactPrice = order -> Double.compare(order.pricePerItem(), price) == 0;

        Predicate<Order> compatible = order ->
                !usedIds.contains(order.id())
                        && order.isActionable()
                        && order.side() == info.getTransaction().getSide()
                        && order.originalAmount() == info.getVolume()
                        && (order.priceExact() ? exactPrice.test(order) : info.isPriceSimilarTo(order.pricePerItem()))
                        && order.canHaveAdvancedTo(entry.filledAmount());

        Predicate<Order> onThisSlot = order -> order.slotPosition().isOnScreenAt(entry.item().slotIndex());

        Comparator<Order> oldestFirst = Comparator.comparingLong(Order::placedAt);

        // Tier 1: still anchored at the slot we last saw it on — the strongest signal we
        // have. Exact price wins over merely-similar if two compatible orders somehow
        // both claim this slot.
        Optional<Order> bySlotExact = candidates.values().stream()
                .filter(compatible.and(exactPrice).and(onThisSlot))
                .min(oldestFirst);

        Optional<Order> bySlotFuzzy = candidates.values().stream()
                .filter(compatible.and(onThisSlot))
                .min(oldestFirst);

        // Tier 2: compatible on side + volume + price, wherever it sits. Exact price
        // preferred over merely-similar — if two compatible orders differ in price, the
        // one matching exactly is far more likely to be the true identity than one that
        // only falls inside the rounding tolerance.
        Optional<Order> byAttributesExact = candidates.values().stream()
                .filter(compatible.and(exactPrice))
                .min(oldestFirst);

        Optional<Order> byAttributesFuzzy = candidates.values().stream()
                .filter(compatible)
                .min(oldestFirst);

        // Tier 3: reconcile a previously-synthesized CoopUnknown order (price unknown at
        // synthesis time) against a fully-filled entry of matching side/volume. Scoped to
        // CoopUnknown specifically — a SelfInCoop/CoopPeer order already has a real price
        // and would have matched in tier 1 or 2 if this entry were actually it.
        Optional<Order> coopUnknown = candidates.values().stream()
                .filter(order -> !usedIds.contains(order.id())
                        && order.isActionable()
                        && order.attribution() instanceof OrderAttribution.CoopUnknown
                        && order.side() == info.getTransaction().getSide()
                        && order.originalAmount() == info.getVolume()
                        && order.isFilled()
                        && order.canHaveAdvancedTo(entry.filledAmount()))
                .min(oldestFirst);

        return bySlotExact
                .or(() -> bySlotFuzzy)
                .or(() -> byAttributesExact)
                .or(() -> byAttributesFuzzy)
                .or(() -> coopUnknown)
                .orElse(null);
    }

    /**
     * Advances a matched order's state from its screen entry, returning the original
     * reference when no field changed.
     *
     * <p><b>Same-reference contract:</b> when every reconciled field equals the stored
     * field, {@code found} is returned unchanged so that {@link Maps#difference}
     * classifies it as {@code entriesInCommon}.
     */
    private static Order reconcileExisting(
            Order found,
            PageOrderParser.ParsedEntry entry,
            BazaarDataOrigin.OrdersScreen origin
    ) {
        int reconciledFill = entry.getFilled(found.filledAmount(), found.originalAmount());

        int reconciledClaimed = entry.getClaimed(
                found.claimedAmount(),
                reconciledFill,
                found.originalAmount()
        );

        OrderStatus reconciledStatus = found.status().reconciledWith(
                found.filledAmount(),
                reconciledFill,
                found.originalAmount(),
                entry.expired(),
                origin.observedAt()
        );

        Optional<Long> reconciledExpiresAt = entry.expiresAt().or(found::expiresAt);

        boolean expiryJustLearned = entry.expiresAt().isPresent()
                && found.expiresAt().isEmpty();
        long reconciledPlacedAt = expiryJustLearned
                ? entry.expiresAt().get() - PageOrderParser.ORDER_EXPIRY_MS
                : found.placedAt();

        OrderAttribution reconciledAttribution =
                found.attribution() instanceof OrderAttribution.CoopUnknown
                        ? entry.attribution()
                        : found.attribution();

        boolean reconciledPriceExact = found.priceExact()
                || Double.compare(found.pricePerItem(), entry.info().getPricePerItem()) == 0;

        double reconciledPrice = found.priceExact()
                ? found.pricePerItem()
                : entry.info().getPricePerItem();

        // Same-reference return — Maps.difference entriesInCommon relies on this.
        if (found.slotPosition().isOnScreenAt(entry.item().slotIndex())
                && found.filledAmount() == reconciledFill
                && found.claimedAmount() == reconciledClaimed
                && found.status() == reconciledStatus
                && found.priceExact() == reconciledPriceExact
                && Double.compare(found.pricePerItem(), reconciledPrice) == 0
                && found.attribution().equals(reconciledAttribution)
                && PageOrderParser.isSameInstant(found.placedAt(), reconciledPlacedAt)
                && PageOrderParser.isSameInstant(found.expiresAt(), reconciledExpiresAt)) {
            return found;
        }

        return new Order(
                found.id(), found.productId(),
                found.side(),
                reconciledPriceExact, reconciledPrice,
                found.originalAmount(),
                reconciledFill, reconciledClaimed,
                new OrderSlotPosition.OnScreen(entry.item().slotIndex()),
                reconciledStatus,
                reconciledPlacedAt, origin.observedAt(), reconciledAttribution,
                reconciledExpiresAt
        );
    }

    /**
     * Constructs a tracked {@link Order} from a screen entry with no prior storage match.
     *
     * <p>{@code placedAt} is supplied by the caller via {@link PageOrderParser.ParsedEntry#resolvePlacedAt}: reversed
     * from the lore-derived {@code expiresAt} when available, otherwise a staggered estimate
     * from {@code observedAt} that preserves FIFO ordering among same-pass siblings.
     * {@code expiresAt} is the lore-derived stamp when present; otherwise {@code null},
     * resolved on demand via {@link Order#effectiveExpiresAt()}.
     */
    private static Order synthesizeNew(PageOrderParser.ParsedEntry entry, String productId, BazaarDataOrigin.OrdersScreen origin, long placedAt) {
        var info = entry.info();

        int claimedAmount = entry.getClaimed(
                0,
                entry.filledAmount(),
                entry.claimableAmount()
        );

        return new Order(
                UUID.randomUUID(), productId,
                info.getTransaction().getSide(),
                true, info.getPricePerItem(),
                info.getVolume(),
                entry.filledAmount(), claimedAmount,
                new OrderSlotPosition.OnScreen(entry.item().slotIndex()),
                entry.observedStatus(),
                placedAt, origin.observedAt(), entry.attribution(),
                entry.expiresAt());
    }

    /**
     * One product's contribution to a batched {@link #commitAll} write, produced by
     * {@link #stageCommit}: the orders it contributes to the combined order list, and the
     * events to fire once that write lands. {@code changed} governs whether
     * {@link BazaarDataUpdateEvent} is posted for this product.
     */
    private record StagedCommit(String productId, List<Order> orders, List<UserOrderEvent> events, BookMutation.MutationOutcome outcome, boolean changed, Set<UUID> confirmed) {}

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
     *                  Update, DataCorrection, and Reanchor entries only. BookOnly and
     *                  None never appear here; book mutation is carried by the
     *                  {@code mutation} parameter.
     * @param preserved orders retained unchanged (grace window / terminal / post-obs).
     * @param offScreen orders retained unchanged (unanchored, no confirmed slot).
     * @param unchanged in-common (state-identical) orders — carried through with no event.
     *                  Pure slot-reanchor orders — both a confirmed reposition and a
     *                  disproven-claim demotion to {@link OrderSlotPosition.OffScreen} —
     *                  arrive as {@link OrderDelta.Reanchor} entries in {@code deltas}
     *                  instead, so their updated {@link OrderSlotPosition} is still
     *                  captured even though they fire no event.
     * @param mutation  pre-composed book mutation for this product — applied here.
     * @param key       the profile these deltas belong to — used to construct every
     *                  {@link UserOrderEvent} this method produces.
     */
    private StagedCommit stageCommit(
            String productId,
            BookMutation<BazaarDataOrigin.OrdersScreen> mutation,
            List<OrderDelta<BazaarDataOrigin.OrdersScreen>> deltas,
            List<Order> preserved,
            List<Order> offScreen,
            List<Order> unchanged,
            BazaarDataOrigin.OrdersScreen origin,
            ProfileKey key) {

        var outcome = mutation.apply(productId, origin);
        boolean bookChanged = outcome.changed();

        var next = new ArrayList<Order>(preserved.size() + offScreen.size() + unchanged.size() + deltas.size());
        next.addAll(preserved);
        next.addAll(offScreen);
        next.addAll(unchanged);

        var events = new ArrayList<UserOrderEvent>();

        for (var delta : deltas) {
            switch (delta) {
                case OrderDelta.Place<BazaarDataOrigin.OrdersScreen> place -> {
                    next.add(place.order());
                    events.add(new UserOrderEvent.Placed(place.order(), key));
                }
                case OrderDelta.Evict<BazaarDataOrigin.OrdersScreen> eviction -> {
                    next.add(eviction.after());
                    events.add(new UserOrderEvent.Cancelled(eviction.after(), key));
                }
                case OrderDelta.Update<BazaarDataOrigin.OrdersScreen> update -> {
                    next.add(update.after());
                    events.addAll(getMutationEvents(update.before(), update.after(), key));
                }
                case OrderDelta.DataCorrection<BazaarDataOrigin.OrdersScreen> correction -> {
                    next.add(correction.after());
                    events.addAll(getMutationEvents(correction.before(), correction.after(), key));
                }
                case OrderDelta.Reanchor<BazaarDataOrigin.OrdersScreen> reanchor -> next.add(reanchor.after());
                default -> {}
            }
        }

        if (!deltas.isEmpty() && NotificationType.ORDERDATA.isEnabled()) {
            long placed = deltas.stream().filter(it -> it instanceof OrderDelta.Place).count();
            long evicted = deltas.stream().filter(it -> it instanceof OrderDelta.Evict).count();
            long updated = deltas.stream().filter(it -> it instanceof OrderDelta.Update).count();
            long expired = deltas.stream().filter(it -> it instanceof OrderDelta.Update u && u.kind() == OrderDelta.Update.UpdateKind.EXPIRY).count();
            long dataCorrected = deltas.stream().filter(it -> it instanceof OrderDelta.DataCorrection).count();
            long reanchored = deltas.stream().filter(it -> it instanceof OrderDelta.Reanchor).count();

            PlayerActionUtil.notifyAll("%s — %s staged: Δ%d placed, Δ%d evicted, Δ%d updated, Δ%d expired, Δ%d data-corrections, Δ%d reanchored".formatted(
                    origin.describe(), productId, placed, evicted, updated, expired, dataCorrected, reanchored), NotificationType.ORDERDATA);
        }

        return new StagedCommit(productId, next, events, outcome, !deltas.isEmpty() || bookChanged, OrderDelta.confirmedFills(deltas));
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
     * <p>
     * Every write in this call belongs to {@code key} — the one profile this whole screen reconciliation is for.
     */
    private void commitAll(List<StagedCommit> staged, BazaarDataOrigin.OrdersScreen origin, ProfileKey key, Set<UUID> observed) {
        if (staged.stream().noneMatch(StagedCommit::changed)) return;

        var next = new ArrayList<Order>(staged.stream().mapToInt(commit -> commit.orders().size()).sum());
        staged.forEach(commit -> next.addAll(commit.orders()));

        var outcomes = staged.stream()
                .filter(StagedCommit::changed)
                .map(StagedCommit::outcome)
                .toList();

        var confirmed = staged.stream()
                .flatMap(commit -> commit.confirmed().stream())
                .collect(Collectors.toUnmodifiableSet());

        FillInference.applyAndSettle(key, outcomes, origin, ignored -> next, confirmed);
        staged.forEach(commit -> commit.events().forEach(event -> event.post(BazaarUtils.EVENT_BUS)));

        staged.stream()
                .filter(StagedCommit::changed)
                .forEach(commit -> new BazaarDataUpdateEvent(commit.productId(), origin).post(BazaarUtils.EVENT_BUS));
    }

    /**
     * Recovers the {@link UserOrderEvent}s implied by one order's before/after diff.
     * Detection stays field-comparison-based — {@link OrderDelta.DataCorrection}
     * shares this method and carries no {@link OrderDelta.Update.UpdateKind} to
     * read — but event CONSTRUCTION now delegates to
     * {@link OrderDelta.Update.UpdateKind#getEvent}, the same lookup
     * {@code OrderDelta.Update} itself is built on, instead of reimplementing it.
     */
    private static List<UserOrderEvent> getMutationEvents(Order before, Order after, ProfileKey key) {
        var result = new ArrayList<UserOrderEvent>();

        if (after.hasExpired() && !before.hasExpired()) {
            result.add(OrderDelta.Update.UpdateKind.EXPIRY.getEvent(before, after, key));

            return result;
        }

        // fillState(), not a direct instanceof — an order can be ALREADY
        // Expired this tick and last tick alike (the EXPIRY branch above only fires
        // on the transition), and still have genuinely advanced its fill on THIS
        // tick's screen read. A direct instanceof against the outer (possibly
        // Expired-wrapped) status would silently miss that advance entirely.
        var afterFill = after.status().fillState();
        var beforeFill = before.status().fillState();

        if (afterFill.filter(it -> it instanceof OrderStatus.Filled).isPresent()
                && beforeFill.filter(it -> it instanceof OrderStatus.Filled).isEmpty()) {
            result.add(OrderDelta.Update.UpdateKind.FILL.getEvent(before, after, key));
        } else if (afterFill.filter(it -> it instanceof OrderStatus.Partial).isPresent()) {
            if (beforeFill.filter(it -> it instanceof OrderStatus.Partial).isEmpty() || after.filledAmount() > before.filledAmount()) {
                result.add(OrderDelta.Update.UpdateKind.FILL.getEvent(before, after, key));
            }
        }

        if (after.claimedAmount() > before.claimedAmount()) {
            result.add(OrderDelta.Update.UpdateKind.CLAIM.getEvent(before, after, key));
        }

        return result;
    }
}