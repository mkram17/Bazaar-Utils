package com.github.mkram17.bazaarutils.data.stored;

import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.storage.ProfileStorage;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class UserOrdersStorage {
    private static final BazaarLogger LOG = BazaarLogger.of(UserOrdersStorage.class);

    private static final ProfileStorage<List<Order>> INSTANCE = new ProfileStorage<>(
            0,
            ArrayList::new,
            "user_orders",
            v -> Codec.list(Order.CODEC).xmap(ArrayList::new, list -> list),
            (it) -> rebuildSlotIndex(it.get())
    );

    private static volatile Map<Integer, Order> slotIndex = Map.of();

    private static void rebuildSlotIndex(List<Order> orders) {
        if (orders == null) {
            slotIndex = Map.of();
            LOG.debug("rebuildSlotIndex: orders null — cleared index");

            return;
        }

        slotIndex = Maps.uniqueIndex(
                orders.stream()
                        .filter(order -> order.isLive() && order.slotPosition().isVisible())
                        .iterator(),
                order -> order.slotPosition().indexIfVisible().getAsInt() // safe as per #isVisible
        );

        LOG.debug("rebuildSlotIndex: {} anchored entries from {} orders", slotIndex.size(), orders.size());
    }

    public static Optional<Order> bySlot(int slot) {
        return Optional.ofNullable(slotIndex.get(slot));
    }

    /**
     * Returns the current order list, or {@code null} if no profile is loaded.
     * Pipeline sources that need to early-return on an unloaded profile use this.
     */
    public static @Nullable List<Order> get() {
        return INSTANCE.get();
    }

    /** {@code true} when a profile is loaded and storage is available. */
    public static boolean isLoaded() {
        return INSTANCE.get() != null;
    }

    /**
     * All tracked orders, or empty if no profile is loaded.
     * Use when null and empty are equivalent to the caller.
     */
    public static List<Order> orders() {
        var it = INSTANCE.get();

        return it != null ? it : List.of();
    }

    /**
     * All active (Set or Partial) orders, or empty if no profile is loaded.
     */
    public static List<Order> active() {
        var it = INSTANCE.get();

        return it == null ? List.of() : it.stream().filter(Order::isActive).toList();
    }

    private UserOrdersStorage() {}

    /**
     * Applies {@code op} to the current storage, writes the result, and persists.
     * Returns the post-persist list (terminal orders already stripped).
     * Returns an empty list if storage is not loaded.
     */
    public static List<Order> apply(StorageOp operation) {
        var storage = INSTANCE.get();
        if (storage == null) return List.of();

        var transformed = operation.apply(new ArrayList<>(storage));

        var filtered = transformed.stream()
                .filter(Order::isLive)
                .collect(Collectors.toCollection(ArrayList::new));

        LOG.info("Persist: {} → {} orders (dropped {} terminal)", transformed.size(), filtered.size(), transformed.size() - filtered.size());

        INSTANCE.set(filtered);
        rebuildSlotIndex(filtered);

        return filtered;
    }

    /** Finds the post-commit copy of {@code ref} in a list returned by {@link #apply}. */
    public static Optional<Order> findById(List<Order> list, UUID id) {
        return list.stream().filter(order -> order.id().equals(id)).findFirst();
    }

    /**
     * A composable storage transformation. Each factory method returns a
     * {@link StorageOp} that transforms a working order list; chain them
     * with {@link #then} and commit atomically via
     * {@link UserOrdersStorage#apply(StorageOp)}.
     */
    @FunctionalInterface
    public interface StorageOp extends UnaryOperator<List<Order>> {

        /** Sequences {@code this} then {@code next}, like {@link Function#andThen}. */
        default StorageOp then(StorageOp next) {
            return list -> next.apply(this.apply(list));
        }

        /** Stamps a cancelled copy of {@code target} in place. */
        static StorageOp cancel(Order target, BazaarDataOrigin origin) {
            var cancelled = target.cancelled(origin);

            return list -> list.stream()
                    .map(order -> order.id().equals(target.id()) ? cancelled : order)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        /** Applies {@code mutation} to the first order matching {@code target} by id. */
        static StorageOp replace(Order target, UnaryOperator<Order> mutation) {
            return list -> list.stream()
                    .map(order -> order.id().equals(target.id()) ? mutation.apply(order) : order)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        /** Appends {@code order} to the list. */
        static StorageOp add(Order order) {
            return list -> {
                var next = new ArrayList<>(list);
                next.add(order);

                return next;
            };
        }

        /** Full reindex — unconditional. */
        static StorageOp reindex() {
            return OrdersPageLayout::reindexActive;
        }

        /**
         * Conditional reindex — only when {@code order} is fully filled.
         * Mirrors the {@code reindexIfFilled} path in the old commit().
         */
        static StorageOp reindexIfFilled(Order order) {
            return order.status() instanceof OrderStatus.Filled
                    ? reindex()
                    : list -> list;
        }
    }
}