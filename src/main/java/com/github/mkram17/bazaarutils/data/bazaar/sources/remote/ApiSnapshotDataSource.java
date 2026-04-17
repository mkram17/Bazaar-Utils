package com.github.mkram17.bazaarutils.data.bazaar.sources.remote;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarApiSnapshotEvent;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataBatchUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Applies batched Hypixel API pricePerItem snapshots to the book store.
 *
 * <p>Per-product logic:
 * <ol>
 *   <li>Splices incoming buy/sell levels into the book (supersession rules apply).</li>
 *   <li>Orphans stale API-sourced levels absent from the current snapshot.</li>
 *   <li>Infers fills for active tracked orders whose pricePerItem level has vanished from
 *       both the snapshot pricePerItem set and the current book. Only runs once a prior
 *       snapshot has been processed — the first snapshot establishes the baseline.</li>
 * </ol>
 */
@DataSource
public final class ApiSnapshotDataSource extends BUListener {

    public ApiSnapshotDataSource() {}

    @Subscription
    public void onApiSnapshotBatch(BazaarApiSnapshotEvent event) {
        long modInitialization = TimeUtil.getModInitTime().toInstant().toEpochMilli();

        var source = new DataSources.ApiSnapshot(event.getTimestamp());

        var changed = new HashSet<String>();
        var allInferredFills = new ArrayList<Order>();

        var storageSnapshot = UserOrdersStorage.INSTANCE.get();

        if (storageSnapshot == null) return;

        var sessionOrders = storageSnapshot.stream()
                .filter(Order::isActive)
                .filter(order -> order.lastUpdatedAt() >= modInitialization)
                .toList();

        for (var entry : event.getSnapshot().entrySet()) {
            String productId = entry.getKey();
            var data = BazaarDataRegistry.getOrCreate(productId);

            var buyLevels = entry.getValue().getKey();
            var sellLevels = entry.getValue().getValue();

            boolean hasHadPriorSnapshot = data.lastApiUpdateTs > 0;

            boolean poolChanged = data.apply(TransactionType.Side.BUY, buyLevels, source);
            poolChanged |= data.apply(TransactionType.Side.SELL, sellLevels, source);
            data.lastApiUpdateTs = event.getTimestamp();

            if (poolChanged) changed.add(productId);

            if (!hasHadPriorSnapshot) continue;

            // Fill inference — price level vanished from both snapshot and book.
            var buyBook  = data.bookFor(TransactionType.of(TransactionType.Side.BUY,  TransactionType.Method.ORDER));
            var sellBook = data.bookFor(TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER));

            sessionOrders.stream()
                    .filter(order -> order.productId().equals(productId))
                    .filter(Order::isActive)
                    .forEach(order -> {
                        var book = order.side() == TransactionType.Side.BUY ? buyBook : sellBook;
                        var level = book.get(order.pricePerItem());

                        if (level == null) {
                            // Level vanished — full fill inference.
                            int remaining = order.originalAmount() - order.filledAmount();
                            allInferredFills.add(order.withFill(remaining));
                            changed.add(productId);
                            PlayerActionUtil.notifyAll(source.describe()
                                            + " | Inferred fill (level absent): %s %dx @ %.1f".formatted(
                                            order.side(), order.originalAmount(), order.pricePerItem()),
                                    NotificationType.BAZAARDATA);
                        } else if (level.orderCount() == 1) {
                            // Sole order at this level — snapshot volume = user's exact remaining amount.
                            int inferredFilled = order.originalAmount() - (int) level.totalVolume();
                            if (inferredFilled > order.filledAmount()) {
                                allInferredFills.add(order.withFill(inferredFilled - order.filledAmount()));
                                changed.add(productId);
                                PlayerActionUtil.notifyAll(source.describe()
                                                + " | Inferred partial fill (sole order): %s %dx @ %.1f | snapshotVol=%d inferredFilled=%d".formatted(
                                                order.side(), order.originalAmount(), order.pricePerItem(),
                                                level.totalVolume(), inferredFilled),
                                        NotificationType.BAZAARDATA);
                            }
                        }
                    });
        }

        if (!allInferredFills.isEmpty()) {
            var fillIds = allInferredFills.stream().map(Order::id).collect(Collectors.toSet());
            var fillMap = allInferredFills.stream().collect(Collectors.toMap(Order::id, o -> o));

            var updatedStorage = UserOrdersStorage.INSTANCE.get();
            if (updatedStorage != null) {
                var withFills = updatedStorage.stream()
                        .map(order -> fillIds.contains(order.id()) ? fillMap.get(order.id()) : order)
                        .collect(Collectors.toCollection(ArrayList::new));

                var reindexed = OrdersPageLayout.reindexActive(withFills);

                UserOrdersStorage.INSTANCE.set(reindexed);

                reindexed.stream()
                        .filter(order -> fillIds.contains(order.id()))
                        .forEach(order -> {
                            switch (order.status()) {
                                case OrderStatus.Filled ignored -> new UserOrderEvent.Filled(order).post(EVENT_BUS);
                                default -> new UserOrderEvent.PartiallyFilled(order).post(EVENT_BUS);
                            }
                        });

                UserOrdersStorage.persist();
            }
        }

        if (!changed.isEmpty()) {
            new BazaarDataBatchUpdateEvent(Collections.unmodifiableSet(changed), source).post(EVENT_BUS);
            PlayerActionUtil.notifyAll(source.describe()
                    + " → " + changed.size() + " products changed.", NotificationType.BAZAARDATA);
        }
    }
}