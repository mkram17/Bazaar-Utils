package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.sources.gui.OrdersScreenDataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Immutable record representing a player's tracked Bazaar order.
 *
 * <h2>Identity</h2>
 * {@code id} is a random {@link UUID} generated at synthesis time — not a Hypixel-side
 * identifier. Stable for the lifetime of the order; never reused after eviction.
 *
 * <h2>Fill / claim floors</h2>
 * {@code filledAmount} and {@code claimedAmount} are monotonically non-decreasing.
 * {@link OrdersScreenDataSource}
 * enforces the floor during screen reconciliation; these mutating helpers do the same
 * for chat-driven updates.
 *
 * <h2>Slot anchoring</h2>
 * {@code lastKnownIndex} is set by {@link OrdersPageLayout#reindexActive}
 * and updated in-place by the screen reconciler. {@link #UNANCHORED} ({@code -1}) means the
 * order was synthesized from a non-screen source and has not yet been seen on the Orders page.
 */
public record Order(
        UUID id,
        String productId,
        TransactionType.Side side,
        double pricePerItem,
        int originalAmount,
        int filledAmount,
        int claimedAmount,
        int lastKnownIndex,
        OrderStatus status,
        long placedAt,
        long lastUpdatedAt
) {
    public static final Codec<Order> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(Order::id),
            Codec.STRING.fieldOf("productId").forGetter(Order::productId),
            TransactionType.Side.CODEC.fieldOf("side").forGetter(Order::side),
            Codec.DOUBLE.fieldOf("pricePerItem").forGetter(Order::pricePerItem),
            Codec.INT.fieldOf("originalAmount").forGetter(Order::originalAmount),
            Codec.INT.fieldOf("filledAmount").forGetter(Order::filledAmount),
            Codec.INT.fieldOf("claimedAmount").forGetter(Order::claimedAmount),
            Codec.INT.fieldOf("lastKnownIndex").forGetter(Order::lastKnownIndex),
            OrderStatus.CODEC.fieldOf("status").forGetter(Order::status),
            Codec.LONG.fieldOf("placedAt").forGetter(Order::placedAt),
            Codec.LONG.fieldOf("lastUpdatedAt").forGetter(Order::lastUpdatedAt)
    ).apply(instance, Order::new));

    public static final int UNANCHORED = -1;

    /**
     * Units not yet filled. Stale until an
     * {@link DataSources.OrdersScreen}
     * observation is reconciled.
     */
    public int unfilledAmount() {
        return originalAmount - filledAmount;
    }

    /**
     * Units (BUY) or coin batches (SELL) that have been filled but not yet claimed.
     */
    public int unclaimedFilled() {
        return filledAmount - claimedAmount;
    }

    /**
     * {@code true} when the order is anchored to a known slot on the Orders page.
     */
    public boolean isAnchored() {
        return lastKnownIndex != UNANCHORED;
    }

    public static Predicate<Order> forProduct(String productId, TransactionType.Side side) {
        return order -> order.productId().equals(productId) && order.side() == side;
    }

    // ── Status predicates ─────────────────────────────────────────────────────

    /**
     * {@code true} when the order has not reached a terminal state.
     * Covers {@link #isActive()} orders and {@link OrderStatus.Filled} orders
     * awaiting claim or flip. Book mutations are only meaningful while live.
     */
    public boolean isLive() {
        return !isTerminal();
    }

    /**
     * {@code true} when the order is still open on the Bazaar —
     * i.e. it can still be matched, partially filled, or cancelled.
     *
     * <p>Does NOT include {@link OrderStatus.Filled} — a filled order is
     * complete and no longer open, but is not terminal either (it still
     * requires a claim or flip action).
     */
    public boolean isActive() {
        return status instanceof OrderStatus.Set || status instanceof OrderStatus.Partial;
    }

    /**
     * {@code true} when the order is fully filled, not yet claimed or flipped.
     */
    public boolean isFilled() {
        return status instanceof OrderStatus.Filled;
    }

    /**
     * {@code true} when this order can be cancelled via the Order Options menu.
     *
     * <p>Hypixel allows cancellation only from {@link OrderStatus.Set} or
     * {@link OrderStatus.Partial}, and only when all filled volume has already
     * been claimed.
     */
    public boolean isCancellable() {
        return isActive() && !isClaimable();
    }

    /**
     * {@code true} when this BUY order can be flipped (its filled items listed as a SELL offer).
     *
     * <p>Conditions (all must hold):
     * <ol>
     *   <li>Side is BUY.</li>
     *   <li>Status is {@link OrderStatus.Filled} — fully filled orders are the flip target;
     *       partial fills are not eligible because Hypixel does not offer a partial flip.</li>
     *   <li>There is at least one unclaimed unit ({@link #unclaimedFilled()} {@code > 0}) —
     *       the items must still be in escrow to flip.</li>
     * </ol>
     */
    public boolean isFlippable() {
        return side == TransactionType.Side.BUY && isFilled() && isClaimable();
    }

    /**
     * {@code true} when the order can be claimed (has filled volume not yet retrieved).
     *
     * <p>Both BUY (items in escrow) and SELL (coins in escrow) orders are claimable.
     * The order does not need to be fully filled — partial claims are valid.
     */
    public boolean isClaimable() {
        return unclaimedFilled() > 0;
    }

    /**
     * {@code true} when the order is in a terminal state and no further actions
     * can be taken on it from within the mod.
     *
     * <p>Terminal states: {@link OrderStatus.Claimed} and {@link OrderStatus.Cancelled}.
     * {@link OrderStatus.Filled} is not terminal — it still requires a claim action.
     */
    public boolean isTerminal() {
        return status instanceof OrderStatus.Claimed || status instanceof OrderStatus.Cancelled;
    }

    /**
     * {@code true} when this order is eligible to be selected in the Order Options screen.
     *
     * <p>Selection rules:
     * <ul>
     *   <li>BUY orders: selectable when active OR when fully filled with items to claim
     *       ({@link #isFlippable()}). Right-click is required by the caller for BUY orders
     *       on the Orders page.</li>
     *   <li>SELL orders: selectable only when active (Set or Partial).</li>
     * </ul>
     */
    public boolean isSelectable() {
        return isCancellable() || isFlippable();
    }

    /**
     * Reports the competitive standing of this order against the current market,
     * accounting for the user's own orders at the same price level.
     *
     * <p>Intended for <b>status display only</b> — not for price calculation.
     * For computing a submission price use
     * {@link PriceInfo#priceForPosition(String, TransactionType, PricingPosition, List)}.
     *
     * <p>COMPETITIVE requires:
     * <ol>
     *   <li>No external orders are ahead of this price in the book.</li>
     *   <li>All volume at this price belongs to the user (external pool count is zero
     *       after subtracting own orders), unless {@link BUConfig#SELF_OUTBIDS} is on.</li>
     * </ol>
     *
     * @param userOrders the caller-supplied snapshot of all tracked orders;
     *                   used to count self-owned positions at this price level
     */
    public Optional<PricingPosition> position(List<Order> userOrders) {
        var transaction = TransactionType.of(side, TransactionType.Method.ORDER);

        var data = BazaarDataRegistry.get(productId);
        if (data == null) return Optional.empty();

        int ahead = data.positionOf(transaction, pricePerItem);
        if (ahead > 0) return Optional.of(PricingPosition.OUTBID);

        var poolOpt = PriceInfo.orderCount(productId, transaction, pricePerItem);
        if (poolOpt.isEmpty()) return Optional.empty();

        int effectiveExternal;

        if (BUConfig.SELF_OUTBIDS) {
            effectiveExternal = poolOpt.getAsInt();
        } else {
            long ownAtPrice = userOrders.stream()
                    .filter(order -> order.productId().equals(productId))
                    .filter(order -> order.side() == side)
                    .filter(Order::isActive)
                    .filter(order -> order.pricePerItem() == pricePerItem)
                    .count();

            effectiveExternal = (int) Math.max(0, poolOpt.getAsInt() - ownAtPrice);
        }

        return Optional.of(effectiveExternal == 0 ? PricingPosition.COMPETITIVE : PricingPosition.MATCHED);
    }

    /**
     * Convenience overload that pulls the current storage snapshot automatically.
     *
     * @see #position(List)
     */
    public Optional<PricingPosition> position() {
        var storage = UserOrdersStorage.INSTANCE.get();

        return position(storage != null ? storage : List.of());
    }

    /**
     * Returns a copy with a different {@code id}.
     * Used when deduplicating synthesized orders against existing storage entries.
     */
    public Order withId(UUID newId) {
        return new Order(newId, productId, side, pricePerItem, originalAmount, filledAmount, claimedAmount, lastKnownIndex, status, placedAt, lastUpdatedAt);
    }

    /**
     * Returns a copy with {@code lastKnownIndex} updated to {@code newIndex}.
     *
     * <p>{@code lastUpdatedAt} is intentionally preserved — slot reanchoring is
     * positional bookkeeping, not a data event, and must not affect eviction logic
     * in {@link OrdersScreenDataSource}.
     */
    public Order reanchored(int newIndex) {
        return new Order(id, productId, side, pricePerItem, originalAmount, filledAmount, claimedAmount, newIndex, status, placedAt, lastUpdatedAt);
    }

    /**
     * Returns a cancelled copy.
     *
     * <p>Only valid when {@link #isCancellable()} is {@code true}; callers are
     * responsible for the guard — this method does not throw on misuse to avoid
     * redundant checks in batch pipelines.
     */
    public Order cancelled() {
        long now = System.currentTimeMillis();

        return new Order(id, productId, side, pricePerItem, originalAmount,
                filledAmount, claimedAmount, lastKnownIndex,
                new OrderStatus.Cancelled(now), placedAt, now);
    }

    /**
     * Returns a copy with {@code amount} added to {@code filledAmount}.
     *
     * <p>Transitions to {@link OrderStatus.Filled} when the new total meets or exceeds
     * {@code originalAmount}; to {@link OrderStatus.Partial} otherwise.
     *
     * <p>{@code filledAmount} is never allowed to exceed {@code originalAmount} —
     * any overshoot (e.g. from a race between chat and screen sources) is clamped.
     */
    public Order withFill(int amount) {
        long now = System.currentTimeMillis();
        int total = Math.min(filledAmount + amount, originalAmount);

        OrderStatus state = total >= originalAmount
                ? new OrderStatus.Filled(now)
                : new OrderStatus.Partial();

        return new Order(id, productId, side, pricePerItem, originalAmount,
                total, claimedAmount, lastKnownIndex, state, placedAt, now);
    }

    /**
     * Returns a copy with {@code amount} added to {@code claimedAmount}.
     *
     * <p>Transitions to terminal {@link OrderStatus.Claimed} when the new claimed
     * total meets or exceeds {@code filledAmount} (not {@code originalAmount}) and
     * the order is already {@link OrderStatus.Filled}. Partial claims preserve the
     * current status.
     *
     * <p>{@code claimedAmount} is clamped to {@code filledAmount} to absorb any
     * overshoot from k/M-rounded screen values.
     */
    public Order withClaim(int amount) {
        long now = System.currentTimeMillis();
        int claim = Math.min(claimedAmount + amount, filledAmount);

        OrderStatus state = (status instanceof OrderStatus.Filled && claim >= filledAmount)
                ? new OrderStatus.Claimed(now)
                : status;

        return new Order(id, productId, side, pricePerItem, originalAmount,
                filledAmount, claim, lastKnownIndex, state, placedAt, now);
    }

    public String describe() {
        String header = "%s %s %dx@%.4f slot=%d".formatted(productId(), side(), originalAmount(), pricePerItem(), lastKnownIndex());

        String status = switch (status()) {
            case OrderStatus.Set ignored -> "Waiting for fills...";
            case OrderStatus.Partial ignored -> "Filled %d / %d".formatted(filledAmount(), originalAmount());
            case OrderStatus.Filled filled -> "Complete — filled @ " + filled.filledAt();
            case OrderStatus.Cancelled ignored -> "Cancelled";
            case OrderStatus.Claimed ignored -> "Claimed";
        };

        return header + " | " + status;
    }
}