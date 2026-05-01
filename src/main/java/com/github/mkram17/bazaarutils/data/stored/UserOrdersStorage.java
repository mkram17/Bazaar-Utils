package com.github.mkram17.bazaarutils.data.stored;

import com.github.mkram17.bazaarutils.data.RenderedOrdersIndex;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.storage.RetentionPolicy;
import com.github.mkram17.bazaarutils.utils.storage.StoragePolicy;
import com.github.mkram17.bazaarutils.utils.storage.profile.PagedProfileStorage;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent;
import tech.thatgravyboat.skyblockapi.helpers.McPlayer;

import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Per-profile persistent storage for the player's tracked Bazaar orders.
 *
 * <p>Every write goes through {@link #apply}: it runs a {@link StorageOp} against a
 * mutable copy of the current list, strips terminal orders (cancelled or claimed) before
 * persisting, and — only when the profile written to is the one currently active —
 * refreshes {@link RenderedOrdersIndex} so the Orders screen reflects the result.
 * Switching profiles refreshes it separately, via {@link Listener}.
 */
public final class UserOrdersStorage {
    private UserOrdersStorage() {}

    /** Schema version 0; eager-loaded, resident, quarantine corrupted files. */
    private static final PagedProfileStorage<List<Order>> STORAGE = new PagedProfileStorage<>(
            0,
            "user_orders",
            List::of,
            (v) -> Codec.list(Order.CODEC).xmap(ArrayList::new, ArrayList::new),
            new StoragePolicy(new RetentionPolicy.Resident(), StoragePolicy.LoadPolicy.EAGER, StoragePolicy.CorruptionPolicy.QUARANTINE)
    );

    /** Refreshes {@link RenderedOrdersIndex} for the newly active profile on every profile switch. */
    @PreInitModule
    public static final class Listener extends BUListener {
        @Subscription
        public void onProfileSwitch(ProfileChangeEvent event) {
            RenderedOrdersIndex.refresh(active(new ProfileKey(McPlayer.INSTANCE.getUuid(), event.getName())));
        }
    }

    /** Returns every stored order for this profile, in whatever order they're persisted in. */
    public static @NotNull List<Order> orders(@NotNull ProfileKey key) {
        return STORAGE.get(key.toIdentity());
    }

    /** Returns this profile's active (non-terminal) orders. */
    public static @NotNull List<Order> active(@NotNull ProfileKey key) {
        return orders(key).stream().filter(Order::isActive).toList();
    }

    /** Every (player, profile) pair with a stored entry — not filtered to the current player. */
    public static @NotNull Set<ProfileKey> knownProfiles() {
        return STORAGE.knownKeys().stream().map(ProfileKey::of).collect(Collectors.toUnmodifiableSet());
    }

    /** Every stored profile's order list, keyed by {@link ProfileKey}. */
    public static @NotNull Map<ProfileKey, List<Order>> allKnown() {
        Map<ProfileKey, List<Order>> result = new LinkedHashMap<>();

        STORAGE.allKnown().forEach((identity, orders) -> result.put(ProfileKey.of(identity), orders));

        return Collections.unmodifiableMap(result);
    }

    /**
     * Applies {@code operation} to {@code key}'s stored order list, strips terminal
     * orders (cancelled or claimed) from the result, persists it, and returns the
     * post-persist list.
     *
     * <p>{@link RenderedOrdersIndex} is refreshed only when {@code key} is the profile
     * currently active — a write to any other profile's storage has nothing on screen
     * to reflect.
     */
    public static @NotNull List<Order> apply(@NotNull ProfileKey key, @NotNull StorageOp operation) {
        var updated = STORAGE.update(key.toIdentity(), current -> {
            var transformed = operation.apply(new ArrayList<>(current));
            var filtered = transformed.stream().filter((order) -> !order.isTerminal()).collect(Collectors.toCollection(ArrayList::new));

            Util.logMessage("Persist[%s]: %d → %d orders (dropped %d terminal)"
                    .formatted(key, transformed.size(), filtered.size(), transformed.size() - filtered.size()));

            return filtered;
        });

        if (key.isCurrent()) RenderedOrdersIndex.refresh(updated);

        return updated;
    }

    /** Returns the order with matching {@code id} in {@code list}. */
    public static Optional<Order> findById(List<Order> list, UUID id) {
        return list.stream().filter(order -> order.id().equals(id)).findFirst();
    }

    /**
     * Composable transformation over a mutable order list. Chain with {@link #then};
     * commit atomically via {@link UserOrdersStorage#apply}. Implementations must not
     * assume the list is sorted or deduplicated.
     */
    @FunctionalInterface
    public interface StorageOp extends UnaryOperator<List<Order>> {

        /** Sequences {@code this} then {@code next}, like {@link Function#andThen}. */
        default StorageOp then(StorageOp next) {
            return list -> next.apply(this.apply(list));
        }

        /** Replaces {@code target} in the list with its cancelled copy. */
        static StorageOp cancel(Order target, BazaarDataOrigin origin) {
            var cancelled = target.cancelled(origin);

            return list -> list.stream()
                    .map(order -> order.id().equals(target.id()) ? cancelled : order)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        /** Replaces the order matching {@code target}'s id with {@code mutation.apply(order)}. */
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
         * Reindexes every active order when {@code before} → {@code after} changes
         * whether this order counts as filled or as live — either shift moves where
         * every other order in the same product/side group is positioned, per
         * {@link OrdersPageLayout#computeScreenSlot}. No-ops otherwise.
         *
         * @param before the order's state immediately prior to this update
         * @param after  the order's state this update produces
         */
        static StorageOp reindexIfOffsets(Order before, Order after) {
            boolean filledChanged = before.isFilled() != after.isFilled();
            boolean liveChanged = before.isLive() != after.isLive();

            return (filledChanged || liveChanged) ? reindex() : list -> list;
        }
    }
}