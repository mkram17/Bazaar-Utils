package com.github.mkram17.bazaarutils.data.bazaar.sources.gui;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.DataSource;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

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
 * <p>After each storage write, the appropriate {@link UserOrderEvent} subtype is fired:
 * <ul>
 *   <li>{@link UserOrderEvent.Placed} — order was synthesized (not previously tracked)</li>
 *   <li>{@link UserOrderEvent.Filled} — existing order transitioned to {@link OrderStatus.Filled}</li>
 *   <li>{@link UserOrderEvent.PartiallyFilled} — existing order transitioned to {@link OrderStatus.Partial}</li>
 *   <li>{@link UserOrderEvent.Claimed} — existing order's {@code claimedAmount} advanced</li>
 * </ul>
 * Status-stable updates and evictions emit no event. Claim and status transition events
 * are independent and may both fire for the same reconcile result.
 */
@DataSource
public final class OrdersScreenDataSource extends BUListener {
    private static final long EVICTION_GRACE_MS = 600;

    /**
     * Captures a reconciled order alongside the pre-reconciliation state needed to
     * determine which {@link UserOrderEvent}s to fire after the storage write is committed.
     *
     * @param order             the reconciled order as it will be stored
     * @param original          the order prior to reconciliation, or {@code null} if the order was synthesized and had no original tracked state
     */
    private record ReconcileResult(Order order, @Nullable Order original) {
        /** {@code true} when this order was synthesized — it had no match in storage. */
        boolean isNew() {
            return original == null;
        }

        boolean changed() {
            return original == null || order != original;
        }

        boolean priceChanged() {
            return original != null && Double.compare(original.pricePerItem(), order.pricePerItem()) != 0;
        }

        /**
         * Posts all applicable {@link UserOrderEvent}s to the event bus.
         *
         * <p>For new orders, only {@link UserOrderEvent.Placed} fires.
         * For existing orders, status-transition events ({@link UserOrderEvent.Filled},
         * {@link UserOrderEvent.PartiallyFilled}) and the claim-advancement event
         * ({@link UserOrderEvent.Claimed}) are independent — both may fire when the
         * screen simultaneously reveals a fill completion and pending claimable items.
         * Neither fires when the respective value is unchanged.
         */
        void postEvents() {
            if (isNew()) {
                new UserOrderEvent.Placed(order).post(EVENT_BUS);

                return;
            }

            if (order.status() instanceof OrderStatus.Cancelled && !(original.status() instanceof OrderStatus.Cancelled)) {
                new UserOrderEvent.Cancelled(order).post(EVENT_BUS);

                return;
            }

            // Status transition — only fire on actual change.
            if (order.status() instanceof OrderStatus.Filled && !(original.status() instanceof OrderStatus.Filled)) {
                new UserOrderEvent.Filled(order).post(EVENT_BUS);
            } else if (order.status() instanceof OrderStatus.Partial) {
                if (!(original.status() instanceof OrderStatus.Partial) || order.filledAmount() > original.filledAmount()) {
                    new UserOrderEvent.PartiallyFilled(order).post(EVENT_BUS);
                }
            }

            // Claim advancement — independent of fill transition.
            if (order.claimedAmount() > original.claimedAmount()) {
                new UserOrderEvent.Claimed(order).post(EVENT_BUS);
            }
        }
    }

    public OrdersScreenDataSource() {}

    @Subscription(priority = Subscription.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    public void onChestLoaded(ChestLoadedEvent event) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) return;

        long now = System.currentTimeMillis();

        var source = new DataSources.OrdersScreen(now);

        List<ItemInfo> items = event.getContainerSlots().stream()
                .map(slot -> new ItemInfo(slot.getContainerSlot(), slot.getItem()))
                .toList();

        List<PageOrderParser.ParsedEntry> parsed = PageOrderParser.parse(items, event.getContainerSlots().size());

        // Group by productId for per-product reconciliation.
        Map<String, List<PageOrderParser.ParsedEntry>> byProduct = new LinkedHashMap<>();
        parsed.forEach(entry -> byProduct
                .computeIfAbsent(entry.info().getProductId(), k -> new ArrayList<>())
                .add(entry));

        parsed.forEach(e -> PlayerActionUtil.notifyAll(
                "[OrdersScreenDataSource] " + e.info().getProductId()
                        + " → " + e.info().getTransaction().getSide()
                        + " " + e.info().getVolume() + "x @ " + e.info().getPricePerItem(),
                NotificationType.BAZAARDATA));

        // Evict products tracked but entirely absent from the screen.
        storage.stream()
                .map(Order::productId)
                .distinct()
                .filter(id -> !byProduct.containsKey(id))
                .filter(id -> storage.stream()
                        .filter(order -> order.productId().equals(id))
                        .allMatch(order -> (source.observedAt() - order.lastUpdatedAt()) > EVICTION_GRACE_MS))
                .forEach(id -> {
                    PlayerActionUtil.notifyAll("Screen evict (absent): " + id, NotificationType.BAZAARDATA);
                    reconcileProduct(id, List.of(), source);
                });

        byProduct.forEach((productId, entries) -> reconcileProduct(productId, entries, source));
    }

    private static void reconcileProduct(String productId, List<PageOrderParser.ParsedEntry> entries, DataSources.OrdersScreen source) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) return;

        var unrelated = storage.stream().filter(order -> !order.productId().equals(productId)).toList();
        var forProduct = storage.stream().filter(order ->  order.productId().equals(productId)).toList();

        var matched = new HashSet<UUID>();

        // Reconcile each screen entry to either a known stored order or a synthesized new one.
        // ReconcileResult carries original state so events can be fired after the storage write.
        var results = entries.stream()
                .map(entry -> {
                    var info = entry.info();

                    // Prefer exact slot index match; fall back to price similarity.
                    Order found = forProduct.stream()
                            .filter(order -> !matched.contains(order.id()))
                            .filter(order -> order.side() == info.getTransaction().getSide())
                            .filter(order -> order.originalAmount() == info.getVolume())
                            .filter(order -> info.isPriceSimilarTo(order.pricePerItem()))
                            .filter(order -> order.lastKnownIndex() == entry.item().slotIndex()) // slot-first
                            .findFirst()
                            .or(() -> forProduct.stream()
                                    .filter(order -> !matched.contains(order.id()))
                                    .filter(order -> order.side() == info.getTransaction().getSide())
                                    .filter(order -> order.originalAmount() == info.getVolume())
                                    .filter(order -> info.isPriceSimilarTo(order.pricePerItem()))
                                    .filter(Order::isActive)
                                    .findFirst())
                            .or(() -> forProduct.stream()
                                    .filter(order -> !matched.contains(order.id()))
                                    .filter(order -> order.side() == info.getTransaction().getSide())
                                    .filter(order -> order.originalAmount() == info.getVolume())
                                    .filter(order -> info.isPriceSimilarTo(order.pricePerItem()))
                                    .findFirst())
                            .orElse(null);

                    ReconcileResult result;
                    if (found != null) {
                        matched.add(found.id());
                        result = new ReconcileResult(reconcileExisting(found, entry, source), found);
                    } else {
                        result = new ReconcileResult(synthesizeNew(entry, productId, source), null);
                    }

                    PlayerActionUtil.notifyAll(
                            "Screen anchor: " + result.order().describe()
                                    + " | slot=" + result.order().lastKnownIndex(),
                            NotificationType.BAZAARDATA);

                    return result;
                })
                .toList();

        // Preserve orders updated after the observation window —
        // e.g. a placement that arrived just as the screen was loading.
        var preserved = forProduct.stream()
                .filter(order -> !matched.contains(order.id()))
                .filter(order -> order.lastUpdatedAt() > source.observedAt())
                .peek(order -> PlayerActionUtil.notifyAll(
                        "Screen evict skipped (recent): " + order.describe()
                                + " | lastUpdatedAt=" + order.lastUpdatedAt()
                                + " > observedAt=" + source.observedAt(),
                        NotificationType.BAZAARDATA))
                .toList();

        // Off-screen orders (logicalPos overflowed the visible grid) are UNANCHORED.
        // They never appear in parsed entries — their absence is not a cancellation.
        var offScreen = forProduct.stream()
                .filter(order -> !matched.contains(order.id()))
                .filter(order -> order.lastUpdatedAt() <= source.observedAt())
                .filter(order -> order.lastKnownIndex() == Order.UNANCHORED)
                .peek(order -> PlayerActionUtil.notifyAll("Screen evict skipped (off-screen; unanchored): %s".formatted(order.describe()), NotificationType.BAZAARDATA))
                .toList();

        var evictedResults = new ArrayList<ReconcileResult>();

        forProduct.stream()
                .filter(Order::isLive)
                .filter(order -> !matched.contains(order.id()))
                .filter(order -> order.lastUpdatedAt() <= source.observedAt())
                .filter(order -> (source.observedAt() - order.lastUpdatedAt()) > EVICTION_GRACE_MS)
                .filter(order -> order.lastKnownIndex() != Order.UNANCHORED) // off-screen ≠ cancelled
                .forEach(order -> {
                    if (order.isFilled()) {
                        evictedResults.add(new ReconcileResult(order.withClaim(order.unclaimedFilled()), order));
                    } else {
                        // Set/Partial disappeared = cancelled. Screen is authoritative.
                        var data = BazaarDataRegistry.get(order.productId());

                        if (data != null) {
                            data.decrement(order.side(), order.pricePerItem(), order.originalAmount() - order.claimedAmount(), source);
                        }

                        evictedResults.add(new ReconcileResult(order.cancelled(), order));
                    }
                });

        boolean anyChange = !evictedResults.isEmpty() || results.stream().anyMatch(ReconcileResult::changed);

        if (!anyChange) return;

        var allResults = Stream.concat(results.stream(), evictedResults.stream()).toList();

        UserOrdersStorage.INSTANCE.set(
                Stream.of(
                                unrelated.stream(),
                                allResults.stream().map(ReconcileResult::order),
                                preserved.stream(),
                                offScreen.stream()
                        )
                        .flatMap(stream -> stream)
                        .collect(Collectors.toCollection(ArrayList::new)));

        // Reprice — move volume from old price to new price (absorbs fill delta implicitly)
        allResults.stream()
                .filter(ReconcileResult::priceChanged)
                .filter(result -> !result.isNew())
                .forEach(result -> {
                    var data = BazaarDataRegistry.get(result.order().productId());
                    if (data == null) return;

                    data.decrement(result.order().side(), result.original().pricePerItem(), result.original().unfilledAmount(), source);
                    data.place(result.order().side(), result.order().pricePerItem(), result.order().unfilledAmount(), source);
                });

        // Fill delta — decrement newly-filled volume at unchanged price
        allResults.stream()
                .filter(result -> !result.isNew() && !result.priceChanged() && result.order().filledAmount() > result.original().filledAmount())
                .forEach(result -> {
                    var data = BazaarDataRegistry.get(result.order().productId());
                    if (data == null) return;

                    int fillDelta = result.order().filledAmount() - result.original().filledAmount();

                    data.decrement(result.order().side(), result.order().pricePerItem(), fillDelta, source);
                });

        allResults.forEach(ReconcileResult::postEvents);
        new BazaarDataUpdateEvent(productId, source).post(EVENT_BUS);

        UserOrdersStorage.persist();
    }

    private static Order reconcileExisting(Order found, PageOrderParser.ParsedEntry entry, DataSources.OrdersScreen source) {
        // Screen is the single source of truth — use its values directly.
        int screenFill = Math.min(entry.filledAmount(), found.originalAmount());
        int reconciledFill = Math.clamp(found.filledAmount(), screenFill, found.originalAmount());
        int screenClaimed = Math.min(entry.claimedAmount(), reconciledFill);
        int reconciledClaimed = Math.clamp(found.claimedAmount(), screenClaimed, reconciledFill);

        OrderStatus reconciledStatus;
        if (reconciledFill >= entry.info().getVolume()) {
            // Preserve the original Filled timestamp when already terminal.
            reconciledStatus = found.status() instanceof OrderStatus.Filled
                    ? found.status()
                    : new OrderStatus.Filled(source.observedAt());
        } else if (reconciledFill > 0) {
            reconciledStatus = found.status() instanceof OrderStatus.Partial
                    ? found.status()
                    : new OrderStatus.Partial();
        } else {
            // Screen shows nothing filled — this corrects any wrongly-set Filled
            // state that slipped through (e.g. a bad match on a original screen load).
            reconciledStatus = found.status() instanceof OrderStatus.Set
                    ? found.status()
                    : new OrderStatus.Set();
        }

        // Nothing changed — return the original reference unchanged.
        if (found.lastKnownIndex() == entry.item().slotIndex()
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
                entry.item().slotIndex(),
                reconciledStatus,
                found.placedAt(), source.observedAt());
    }

    private static Order synthesizeNew(PageOrderParser.ParsedEntry entry, String productId, DataSources.OrdersScreen source) {
        var info = entry.info();
        int filledAmount = entry.filledAmount();
        int claimedAmount = entry.claimedAmount();

        OrderStatus status;
        if (filledAmount >= info.getVolume()) {
            status = new OrderStatus.Filled(source.observedAt());
        } else if (filledAmount > 0) {
            status = new OrderStatus.Partial();
        } else {
            status = new OrderStatus.Set();
        }

        return new Order(
                UUID.randomUUID(), productId,
                info.getTransaction().getSide(),
                info.getPricePerItem(),
                info.getVolume(),
                filledAmount, claimedAmount,
                entry.item().slotIndex(),
                status,
                source.observedAt(), source.observedAt());
    }
}