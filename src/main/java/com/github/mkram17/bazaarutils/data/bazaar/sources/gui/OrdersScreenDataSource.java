package com.github.mkram17.bazaarutils.data.bazaar.sources.gui;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.RenderedOrdersAPI;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.stored.ProfileFlagsStorage;
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
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
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
 * grace window on an exhaustive side are evicted), container slot position (stamped directly;
 * no reindex), fill and claim counts (floor only; stored value never regressed), and newly
 * observed orders (synthesized when no stored match exists).
 *
 * <p>Resolution runs in two phases before any storage write. Phase 1
 * ({@link #matchScreenEntries}) matches screen entries against stored orders; its
 * per-product outputs are assembled into a single slot map in {@link #onContainerLoaded}.
 * Phase 2–4 ({@link #reconcileProduct}) classifies unmatched stored orders against that
 * map — a slot occupied by another product's order disproves a cached position without
 * any separate verification step.
 *
 * <p>All book mutations for a product are composed into one {@link BookMutation}
 * chain passed to {@link #stageCommit}. Storage, events, and
 * {@link BazaarDataUpdateEvent} are handled by {@link #commitAll}.
 */
@DataSource
public final class OrdersScreenDataSource extends BUListener {
    /**
     * Minimum age of the last update, in milliseconds, before a stored order can be evicted by
     * screen absence. Guards against the race where a chat event (fill, claim, cancel) stamps
     * {@code lastUpdatedAt} right before the container-loaded event fires.
     */
    private static final long EVICTION_GRACE_MS = 600L;

    public OrdersScreenDataSource() {}

    /**
     * Reconciles every product's tracked orders against this tick's Orders page render.
     */
    @Subscription(priority = Priority.HIGHEST)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    public void onContainerLoaded(ContainerLoadedEvent event) {
        var origin = new BazaarDataOrigin.OrdersScreen(System.currentTimeMillis());

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        boolean previouslyKnownCoop = ProfileFlagsStorage.isKnownCoop(key);
        boolean isTitleCoop = event.getTitle().equals("Co-op Bazaar Orders");
        boolean isKnownCoop = previouslyKnownCoop || isTitleCoop;

        List<ItemInfo> items = event.getContainerSlots().stream()
                .map(slot -> new ItemInfo(slot.getContainerSlot(), slot.getItem()))
                .toList();

        String localPlayerName = McPlayer.INSTANCE.getName();

        List<PageOrderParser.ParsedEntry> parsed = PageOrderParser.parse(items, event.getContainerSlots().size(), key, localPlayerName, isKnownCoop, origin);

        if (!previouslyKnownCoop && (isTitleCoop || parsed.stream().anyMatch(entry -> entry.attribution().isCoopContext()))) {
            ProfileFlagsStorage.markObservedCoop(key);
        }

        long unparsedOccupied = items.stream()
                .filter(item -> !item.isEmpty())
                .filter(item -> OrdersPageLayout.isOrderSlot(item.slotIndex(), event.getContainerSlots().size()))
                .count() - parsed.size();

        int totalBuy = (int) parsed.stream().filter(entry -> entry.info().side() == TransactionType.Side.BUY).count();
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

        var touched = new LinkedHashSet<>(byProduct.keySet());
        touched.addAll(storageByProduct.keySet());

        // ── Phase 1 for every touched product, first ───────────────────────────
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

        var committed = commitAll(staged, origin, key);
        List<Order> finalOrders = committed != null ? committed : storage;

        // Rebuild onScreenBySlot from post-settlement orders so RenderedOrdersAPI.get(slot)
        // never disagrees with RenderedOrdersAPI.orders() if FillInference advanced a visible order.
        Map<UUID, Order> finalById = finalOrders.stream()
                .collect(Collectors.toMap(Order::id, order -> order, (a, b) -> b));
        var finalOnScreenBySlot = new HashMap<Integer, Order>(onScreenBySlot.size());
        onScreenBySlot.forEach((slot, order) -> {
            Order latest = finalById.getOrDefault(order.id(), order);
            if (latest.isActionable()) {
                finalOnScreenBySlot.put(slot, latest);
            }
        });

        RenderedOrdersAPI.update(finalOnScreenBySlot, finalOrders, key);
    }

    /**
     * Phase 1 for one product: matches this tick's screen entries against its stored
     * orders, synthesizing an order for any entry with no match. Pure computation —
     * touches neither storage nor the book.
     */
    private ProductMatch matchScreenEntries(String productId, List<Order> storage, List<PageOrderParser.ParsedEntry> entries, BazaarDataOrigin.OrdersScreen origin) {
        Map<UUID, Order> storedById = storage.stream()
                .collect(Collectors.toMap(Order::id, order -> order));

        var ordered = entries.stream()
                .sorted(Comparator.comparingInt(entry -> entry.item().slotIndex()))
                .toList();

        var usedIds = new HashSet<UUID>();
        var reconciledById = new LinkedHashMap<UUID, Order>();

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
     */
    private StagedCommit reconcileProduct(ProductMatch match, Map<Integer, Order> onScreenBySlot, BazaarDataOrigin.OrdersScreen origin, ProfileKey key, ExhaustiveSides exhaustive) {
        String productId = match.productId();
        Map<UUID, Order> storedById = match.storedById();
        Map<UUID, Order> reconciledById = match.reconciledById();
        Map<Integer, Order> reconciledBySlot = match.reconciledBySlot();

        var diff = Maps.difference(storedById, reconciledById);

        var preserved = new ArrayList<Order>();
        var offScreen = new ArrayList<Order>();
        var deltas = new ArrayList<OrderDelta<BazaarDataOrigin.OrdersScreen>>();
        BookMutation<BazaarDataOrigin.OrdersScreen> mutation = BookMutation.none();

        // ── Phase 3a: classify entriesOnlyOnLeft ─────────────────────────────
        for (var kv : diff.entriesOnlyOnLeft().entrySet()) {
            var order = kv.getValue();

            if (order.isTerminal()) continue;

            // Local before global: same-product Phase 1 output first, then the full screen map.
            Order sibling = null, screenOccupant = null;
            if (order.slotPosition() instanceof OrderSlotPosition.OnScreen(int slot)) {
                sibling = reconciledBySlot.get(slot);
                if (sibling == null) screenOccupant = onScreenBySlot.get(slot);
            }

            // Never evict when the render is non-exhaustive for this side (overflow or unparsed slots).
            if (!exhaustive.forSide(order.side())) {
                Order occupant = sibling != null ? sibling : screenOccupant;
                if (occupant != null) {
                    var demoted = order.reanchored(new OrderSlotPosition.OffScreen(0));
                    deltas.add(new OrderDelta.Reanchor<>(order, demoted));

                    PlayerActionUtil.notifyAll("%s — Reanchored off-screen (non-exhaustive side) — slot held by %s: %s".formatted(
                            origin.describe(), occupant.describe(), order.describe()), NotificationType.ORDERDATA);
                } else {
                    PlayerActionUtil.notifyAll("%s — Skipped — non-exhaustive side: %s".formatted(origin.describe(), order.describe()), NotificationType.ORDERDATA);
                    offScreen.add(order);
                }
                continue;
            }

            boolean pastGrace = (origin.observedAt() - order.lastUpdatedAt()) >= EVICTION_GRACE_MS;

            if (pastGrace) {
                if (order.isFilled()) {
                    // Always call withClaim (even when unclaimedFilled == 0) so already-filled orders
                    // transition cleanly to Terminal Claimed instead of remaining as zombies.
                    Order settled = order.withClaim(order.unclaimedFilled(), origin);
                    deltas.add(OrderDelta.Update.claim(order, settled));

                    PlayerActionUtil.notifyAll("%s — Auto-claimed %d units (left screen filled%s): %s".formatted(
                            origin.describe(), order.unclaimedFilled(),
                            sibling != null ? " — slot confirmed held by " + sibling.describe() : "",
                            order.describe()), NotificationType.ORDERDATA);
                } else {
                    Order settled = order.unclaimedFilled() > 0
                            ? order.withClaim(order.unclaimedFilled(), origin)
                            : order;

                    var transaction = TransactionType.of(order.side(), TransactionType.Method.ORDER);

                    // Only withdraw from the book if the order was still resting on it (not already Expired).
                    BookMutation<BazaarDataOrigin.OrdersScreen> cancelMutation = order.isOpen()
                            ? BookMutation.withdrawn(transaction, order.pricePerItem(), order.unfilledAmount())
                            : BookMutation.none();

                    mutation = mutation.then(cancelMutation);
                    deltas.add(new OrderDelta.Evict<>(order, settled.cancelled(origin), cancelMutation));

                    PlayerActionUtil.notifyAll("%s — Cancelled — %s%s: %s".formatted(
                            origin.describe(),
                            sibling != null ? "slot confirmed held by a different order of this same product" : "disappeared from screen",
                            settled.claimedAmount() > order.claimedAmount() ? " (" + order.unclaimedFilled() + " filled units claimed with it)" : "",
                            order.describe()), NotificationType.ORDERDATA);

                    if (order.isOpen() && order.unfilledAmount() > 0) {
                        PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (evicted)".formatted(
                                        origin.describe(),
                                        transaction.getPriceType(),
                                        productId, order.unfilledAmount(), order.pricePerItem()),
                                NotificationType.BAZAARDATA);
                    }
                }
            } else if (sibling != null || screenOccupant != null) {
                Order occupant = sibling != null ? sibling : screenOccupant;
                var demoted = order.reanchored(new OrderSlotPosition.OffScreen(0));
                deltas.add(new OrderDelta.Reanchor<>(order, demoted));

                PlayerActionUtil.notifyAll("%s — Reanchored off-screen (position only — inside grace window) — slot confirmed held by %s: %s".formatted(
                        origin.describe(), occupant.describe(), order.describe()), NotificationType.ORDERDATA);
            } else {
                preserved.add(order);
            }
        }

        // ── Phase 3b: synthesized orders ─────────────────────────────────────
        for (var order : diff.entriesOnlyOnRight().values()) {
            deltas.add(new OrderDelta.Place<>(order, BookMutation.none()));
        }

        var unchanged = new ArrayList<>(diff.entriesInCommon().values());

        // ── Phase 3c: differing matched orders ───────────────────────────────
        for (var entry : diff.entriesDiffering().entrySet()) {
            var before = entry.getValue().leftValue();
            var after = entry.getValue().rightValue();

            boolean freshlyExpired = after.hasExpired() && !before.hasExpired();
            boolean filledMore = after.filledAmount() > before.filledAmount();
            boolean claimedMore = after.claimedAmount() > before.claimedAmount();
            boolean pricedDifferently = Double.compare(before.pricePerItem(), after.pricePerItem()) != 0;
            boolean priceSettled = !before.price().equals(after.price());
            boolean attributionResolved = !before.attribution().equals(after.attribution());

            var transaction = TransactionType.of(after.side(), TransactionType.Method.ORDER);

            // Compose book mutations independently so simultaneous transitions (e.g. fill + expiry)
            // never drop one side's decrement.
            BookMutation<BazaarDataOrigin.OrdersScreen> orderMutation = BookMutation.none();
            if (pricedDifferently && before.isOpen()) {
                orderMutation = BookMutation.withdrawn(transaction, before.pricePerItem(), before.unfilledAmount());
                if (before.unfilledAmount() > 0) {
                    PlayerActionUtil.notifyAll("%s — Book re-priced: %s %s Δ%d @ %.4f → %.4f".formatted(
                                    origin.describe(),
                                    transaction.getPriceType(),
                                    productId, before.unfilledAmount(), before.pricePerItem(), after.pricePerItem()),
                            NotificationType.BAZAARDATA);
                }
            } else if (freshlyExpired && before.isOpen()) {
                if (filledMore) {
                    int filledDelta = after.filledAmount() - before.filledAmount();
                    orderMutation = BookMutation.filled(after.id(), transaction, after.pricePerItem(), filledDelta, after.isFilled());
                }
                if (after.unfilledAmount() > 0) {
                    orderMutation = orderMutation.then(BookMutation.withdrawn(transaction, after.pricePerItem(), after.unfilledAmount()));
                }
            } else if (filledMore && before.isOpen()) {
                int filledDelta = after.filledAmount() - before.filledAmount();
                orderMutation = BookMutation.filled(after.id(), transaction, after.pricePerItem(), filledDelta, after.isFilled());
            }

            mutation = mutation.then(orderMutation);

            if (freshlyExpired) {
                deltas.add(OrderDelta.Update.expiry(before, after, orderMutation));

                PlayerActionUtil.notifyAll("%s — Expired (Δunfilled=%d @ %.4f decremented from book): %s".formatted(
                        origin.describe(), after.unfilledAmount(), after.pricePerItem(),
                        after.describe()), NotificationType.ORDERDATA);

                if (before.isOpen() && after.unfilledAmount() > 0) {
                    PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (order expired)".formatted(
                                    origin.describe(),
                                    transaction.getPriceType(),
                                    productId, after.unfilledAmount(), after.pricePerItem()),
                            NotificationType.BAZAARDATA);
                }
            } else if (filledMore) {
                int filledDelta = after.filledAmount() - before.filledAmount();
                deltas.add(OrderDelta.Update.fill(before, after, orderMutation));

                PlayerActionUtil.notifyAll("%s — Fill advanced %d → %d (Δ%d): %s".formatted(
                        origin.describe(), before.filledAmount(), after.filledAmount(),
                        filledDelta, after.describe()), NotificationType.ORDERDATA);

                if (before.isOpen()) {
                    PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (fill advance)".formatted(
                                    origin.describe(),
                                    transaction.getPriceType(),
                                    productId, filledDelta, after.pricePerItem()),
                            NotificationType.BAZAARDATA);
                }
            } else if (claimedMore) {
                int claimedDelta = after.claimedAmount() - before.claimedAmount();
                deltas.add(OrderDelta.Update.claim(before, after));

                PlayerActionUtil.notifyAll("%s — Claim advanced %d → %d (Δ%d): %s".formatted(
                        origin.describe(), before.claimedAmount(), after.claimedAmount(),
                        claimedDelta, after.describe()), NotificationType.ORDERDATA);
            } else if (priceSettled || attributionResolved) {
                deltas.add(new OrderDelta.DataCorrection<>(before, after, orderMutation));

                if (priceSettled) {
                    PlayerActionUtil.notifyAll("%s — Price settled %.4f → %.4f (exact=%b): %s".formatted(
                            origin.describe(), before.pricePerItem(), after.pricePerItem(), after.isPriceExact(),
                            after.describe()), NotificationType.ORDERDATA);
                }
            } else {
                deltas.add(new OrderDelta.Reanchor<>(before, after));
            }
        }

        // ── Phase 3d: floor affirmation ──────────────────────────────────────
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
                    .map(e -> new PriceLevel<>(
                            e.getKey(),
                            e.getValue().stream().mapToLong(Order::unfilledAmount).sum(),
                            e.getValue().size(),
                            origin))
                    .toList();

            mutation = mutation.then(BookMutation.floor(transaction, levels));
        }

        return stageCommit(productId, mutation, deltas, preserved, offScreen, unchanged, origin, key);
    }

    private record ProductMatch(String productId, Map<UUID, Order> storedById, Map<UUID, Order> reconciledById, Map<Integer, Order> reconciledBySlot) {}

    private static Map<Integer, Order> indexBySlot(Map<UUID, Order> reconciledById) {
        var bySlot = new HashMap<Integer, Order>();

        for (var order : reconciledById.values()) {
            if (!(order.slotPosition() instanceof OrderSlotPosition.OnScreen(int slot))) continue;
            bySlot.put(slot, order);
        }

        return Map.copyOf(bySlot);
    }

    /**
     * Finds the best stored order to match against a screen entry lazily across tiers:
     * slot+exact → slot+fuzzy → attributes+exact → attributes+fuzzy → filled CoopUnknown.
     */
    private static @Nullable Order findMatch(Map<UUID, Order> candidates, Set<UUID> usedIds, PageOrderParser.ParsedEntry entry) {
        var info = entry.info();
        double price = info.getPricePerItem();

        Predicate<Order> exactPrice = Order.forExactPrice(price);

        Predicate<Order> compatible = order ->
                !usedIds.contains(order.id())
                        && order.isActionable()
                        && order.side() == info.side()
                        && order.originalAmount() == info.getVolume()
                        && Order.forPrice(info.price()).test(order)
                        && order.canHaveAdvancedTo(entry.filledAmount());

        Predicate<Order> onThisSlot = order -> order.slotPosition().isOnScreenAt(entry.item().slotIndex());

        Comparator<Order> oldestFirst = Comparator.comparingLong(Order::placedAt);

        return candidates.values().stream()
                .filter(compatible.and(exactPrice).and(onThisSlot))
                .min(oldestFirst)
                .or(() -> candidates.values().stream()
                        .filter(compatible.and(onThisSlot))
                        .min(oldestFirst))
                .or(() -> candidates.values().stream()
                        .filter(compatible.and(exactPrice))
                        .min(oldestFirst))
                .or(() -> candidates.values().stream()
                        .filter(compatible)
                        .min(oldestFirst))
                .or(() -> entry.observedStatus().isFilled()
                        ? candidates.values().stream()
                        .filter(order -> !usedIds.contains(order.id())
                                && order.isActionable()
                                && order.attribution() instanceof OrderAttribution.CoopUnknown
                                && order.side() == info.side()
                                && order.originalAmount() == info.getVolume()
                                && order.isFilled()
                                && order.canHaveAdvancedTo(entry.filledAmount()))
                        .min(oldestFirst)
                        : Optional.empty())
                .orElse(null);
    }

    /**
     * Advances a matched order's state from its screen entry, returning the original
     * reference when no field changed.
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

        // Prefer existing exact expiresAt over hour-rounded lore countdowns to prevent
        // repeated ±59m expiry drift and reanchor thrashing.
        Optional<Long> reconciledExpiresAt = found.expiresAt().or(entry::expiresAt);

        boolean expiryJustLearned = entry.expiresAt().isPresent()
                && found.expiresAt().isEmpty();
        long reconciledPlacedAt = expiryJustLearned
                ? entry.expiresAt().get() - Order.ORDER_EXPIRY_MS
                : found.placedAt();

        OrderAttribution reconciledAttribution =
                found.attribution() instanceof OrderAttribution.CoopUnknown
                        ? entry.attribution()
                        : found.attribution();

        PriceInfo reconciledPrice = found.isPriceExact()
                ? found.price()
                : entry.info().price();

        // Same-reference return — Maps.difference entriesInCommon relies on this.
        if (found.slotPosition().isOnScreenAt(entry.item().slotIndex())
                && found.filledAmount() == reconciledFill
                && found.claimedAmount() == reconciledClaimed
                && found.status().equals(reconciledStatus)
                && found.price().equals(reconciledPrice)
                && found.attribution().equals(reconciledAttribution)
                && PageOrderParser.isSameInstant(found.placedAt(), reconciledPlacedAt)
                && PageOrderParser.isSameInstant(found.expiresAt(), reconciledExpiresAt)) {
            return found;
        }

        return new Order(
                found.id(), found.productId(),
                found.side(),
                reconciledPrice,
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
     */
    private static Order synthesizeNew(PageOrderParser.ParsedEntry entry, String productId, BazaarDataOrigin.OrdersScreen origin, long placedAt) {
        var info = entry.info();

        int claimedAmount = entry.getClaimed(
                0,
                entry.filledAmount(),
                info.getVolume()
        );

        return new Order(
                UUID.randomUUID(), productId,
                info.side(),
                info.price(),
                info.getVolume(),
                entry.filledAmount(), claimedAmount,
                new OrderSlotPosition.OnScreen(entry.item().slotIndex()),
                entry.observedStatus(),
                placedAt, origin.observedAt(), entry.attribution(),
                entry.expiresAt());
    }

    private record StagedCommit(
            String productId,
            List<Order> orders,
            List<OrderDelta<BazaarDataOrigin.OrdersScreen>> deltas,
            BookMutation.MutationOutcome outcome,
            boolean orderChanged,
            boolean bookChanged
    ) {
        boolean anyChanged() {
            return orderChanged || bookChanged;
        }
    }

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

        for (var delta : deltas) {
            switch (delta) {
                case OrderDelta.Place<BazaarDataOrigin.OrdersScreen> place -> next.add(place.order());
                case OrderDelta.Evict<BazaarDataOrigin.OrdersScreen> eviction -> next.add(eviction.after());
                case OrderDelta.Update<BazaarDataOrigin.OrdersScreen> update -> next.add(update.after());
                case OrderDelta.DataCorrection<BazaarDataOrigin.OrdersScreen> correction -> next.add(correction.after());
                case OrderDelta.Reanchor<BazaarDataOrigin.OrdersScreen> reanchor -> next.add(reanchor.after());
                case OrderDelta.BookOnly<BazaarDataOrigin.OrdersScreen> ignored -> {}
                case OrderDelta.Swap<BazaarDataOrigin.OrdersScreen> ignored -> {}
            }
        }

        if (!deltas.isEmpty() && NotificationType.ORDERDATA.isEnabled()) {
            long placed = deltas.stream().filter(it -> it instanceof OrderDelta.Place).count();
            long evicted = deltas.stream().filter(it -> it instanceof OrderDelta.Evict).count();
            long expired = deltas.stream().filter(it -> it instanceof OrderDelta.Update u && u.kind() == OrderDelta.Update.UpdateKind.EXPIRY).count();
            long updated = deltas.stream().filter(it -> it instanceof OrderDelta.Update u && u.kind() != OrderDelta.Update.UpdateKind.EXPIRY).count();
            long dataCorrected = deltas.stream().filter(it -> it instanceof OrderDelta.DataCorrection).count();
            long reanchored = deltas.stream().filter(it -> it instanceof OrderDelta.Reanchor).count();

            PlayerActionUtil.notifyAll("%s — %s staged: Δ%d placed, Δ%d evicted, Δ%d updated, Δ%d expired, Δ%d data-corrections, Δ%d reanchored".formatted(
                    origin.describe(), productId, placed, evicted, updated, expired, dataCorrected, reanchored), NotificationType.ORDERDATA);
        }

        return new StagedCommit(productId, next, List.copyOf(deltas), outcome, !deltas.isEmpty(), bookChanged);
    }

    /**
     * Writes every {@link #stageCommit} result from one screen load in a single atomic
     * storage write, settles fill inference, fires post-settlement {@link UserOrderEvent}s,
     * and posts {@link BazaarDataUpdateEvent} only for products whose book or inferred
     * orders actually changed.
     */
    private @Nullable List<Order> commitAll(List<StagedCommit> staged, BazaarDataOrigin.OrdersScreen origin, ProfileKey key) {
        if (staged.stream().noneMatch(StagedCommit::anyChanged)) return null;

        var next = new ArrayList<Order>(staged.stream().mapToInt(commit -> commit.orders().size()).sum());
        staged.forEach(commit -> next.addAll(commit.orders()));

        var outcomes = staged.stream()
                .filter(StagedCommit::bookChanged)
                .map(StagedCommit::outcome)
                .toList();

        var result = FillInference.applyAndSettle(key, outcomes, origin, ignored -> next);
        List<Order> committed = result.ordersOrElse(key, next);

        Map<UUID, Order> committedById = committed.stream()
                .collect(Collectors.toMap(Order::id, order -> order, (a, b) -> b));

        for (var commit : staged) {
            for (var delta : commit.deltas()) {
                postDeltaEvents(delta, committedById, key);
            }
        }

        var changedProducts = new LinkedHashSet<>(result.changedProducts());
        staged.stream()
                .filter(StagedCommit::bookChanged)
                .map(StagedCommit::productId)
                .forEach(changedProducts::add);

        changedProducts.forEach(productId -> new BazaarDataUpdateEvent(productId, origin).post(BazaarUtils.EVENT_BUS));

        return committed;
    }

    private static void postDeltaEvents(
            OrderDelta<BazaarDataOrigin.OrdersScreen> delta,
            Map<UUID, Order> committedById,
            ProfileKey key) {
        switch (delta) {
            case OrderDelta.Place<BazaarDataOrigin.OrdersScreen> place -> {
                Order resolved = committedById.getOrDefault(place.order().id(), place.order());
                new UserOrderEvent.Placed(resolved, key).post(BazaarUtils.EVENT_BUS);
            }
            case OrderDelta.Evict<BazaarDataOrigin.OrdersScreen> eviction -> {
                if (eviction.after().claimedAmount() > eviction.before().claimedAmount()) {
                    int claimedDelta = eviction.after().claimedAmount() - eviction.before().claimedAmount();
                    new UserOrderEvent.Claimed(eviction.after(), claimedDelta, key).post(BazaarUtils.EVENT_BUS);
                }
                new UserOrderEvent.Cancelled(eviction.after(), key).post(BazaarUtils.EVENT_BUS);
            }
            case OrderDelta.Update<BazaarDataOrigin.OrdersScreen> update -> {
                Order resolved = committedById.getOrDefault(update.after().id(), update.after());
                getMutationEvents(update.before(), resolved, key).forEach(e -> e.post(BazaarUtils.EVENT_BUS));
            }
            case OrderDelta.DataCorrection<BazaarDataOrigin.OrdersScreen> correction -> {
                Order resolved = committedById.getOrDefault(correction.after().id(), correction.after());
                getMutationEvents(correction.before(), resolved, key).forEach(e -> e.post(BazaarUtils.EVENT_BUS));
            }
            case OrderDelta.Reanchor<BazaarDataOrigin.OrdersScreen> ignored -> {}
            case OrderDelta.BookOnly<BazaarDataOrigin.OrdersScreen> ignored -> {}
            case OrderDelta.Swap<BazaarDataOrigin.OrdersScreen> ignored -> {}
        }
    }

    /**
     * Recovers all {@link UserOrderEvent}s implied by one order's before/after diff,
     * including simultaneous transitions (e.g. expiry + fill advance + claim advance).
     */
    private static List<UserOrderEvent> getMutationEvents(Order before, Order after, ProfileKey key) {
        var result = new ArrayList<UserOrderEvent>();

        if (after.hasExpired() && !before.hasExpired()) {
            result.add(OrderDelta.Update.UpdateKind.EXPIRY.getEvent(before, after, key));
        }

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
