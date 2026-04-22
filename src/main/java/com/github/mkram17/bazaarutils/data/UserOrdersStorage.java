package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.storage.ProfileStorage;
import com.mojang.serialization.Codec;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class UserOrdersStorage {
    private static final BazaarLogger LOG = BazaarLogger.of(UserOrdersStorage.class);

    public static final ProfileStorage<List<Order>> INSTANCE = new ProfileStorage<>(
            0,
            ArrayList::new,
            "user_orders",
            v -> Codec.list(Order.CODEC).xmap(ArrayList::new, list -> list)
    );

    // O(1) slot → order lookup, rebuilt whenever the backing list changes.
    private static volatile Map<Integer, Order> slotIndex = Map.of();

    private static void rebuildSlotIndex(List<Order> orders) {
        if (orders == null) {
            slotIndex = Map.of();
            LOG.info("rebuildSlotIndex: orders null — cleared index");
            return;
        }

        Map<Integer, Order> indexes = new HashMap<>(orders.size() * 2);

        for (Order order : orders) {
            if (order.lastKnownIndex() != Order.UNANCHORED) {
                indexes.put(order.lastKnownIndex(), order);
            }
        }
        slotIndex = Collections.unmodifiableMap(indexes);
        LOG.info("rebuildSlotIndex: {} anchored entries from {} orders", indexes.size(), orders.size());
    }

    public static Optional<Order> getOrderFromSlotIndex(int slotIndex) {
        return Optional.ofNullable(UserOrdersStorage.slotIndex.get(slotIndex));
    }

    private UserOrdersStorage() {}

    public static Optional<Order> replace(Order target, UnaryOperator<Order> mutation) {
        var storage = INSTANCE.get();
        if (storage == null) return Optional.empty();

        Order[] result = {null};
        var updated = storage.stream()
                .map(order -> {
                    if (!order.id().equals(target.id())) return order;

                    return result[0] = mutation.apply(order);
                })
                .collect(Collectors.toCollection(ArrayList::new));

        INSTANCE.set(updated);

        return Optional.ofNullable(result[0]);
    }

    /**
     * Drops terminal orders (Claimed/Cancelled) from the active profile and persists.
     * ProfileStorage.set() calls save() internally — no extra save() needed.
     */
    public static void persist() {
        List<Order> loaded = INSTANCE.get();
        if (loaded == null) return;

        List<Order> filtered = loaded.stream()
                .filter(Order::isLive)
                .collect(Collectors.toCollection(ArrayList::new));

        LOG.info("Persist: {} → {} orders (dropped {} terminal)", loaded.size(), filtered.size(), loaded.size() - filtered.size());

        INSTANCE.set(filtered);
        rebuildSlotIndex(filtered);
    }

    /**
     * Cancels {@code matched}, fully reindexes, and returns the cancelled copy.
     * Full reindex is required because a SELL cancel shifts all BUY slot offsets.
     *
     * <p>Pool decrement and event firing remain the responsibility of the call site.
     */
    public static Order cancelAndReindex(Order matched) {
        List<Order> loaded = INSTANCE.get();
        if (loaded == null) return matched.cancelled();

        var cancelled = matched.cancelled();

        var afterCancel = loaded.stream()
                .map(order -> matched.id().equals(order.id()) ? cancelled : order)
                .collect(Collectors.toCollection(ArrayList::new));

        List<Order> reindexed = OrdersPageLayout.reindexActive(afterCancel);
        INSTANCE.set(reindexed);
        rebuildSlotIndex(reindexed);

        return cancelled;
    }

    public static Order findAfterReindex(List<Order> reindexed, Order original) {
        return reindexed.stream()
                .filter(o -> o.id().equals(original.id()))
                .findFirst()
                .orElse(original);
    }
}