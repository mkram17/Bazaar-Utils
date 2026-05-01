package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataQuery;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceQuantity;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Immutable snapshot of a player's tracked Bazaar order.
 *
 * <p>{@code id} is a random {@link UUID} assigned at synthesis time — not a Hypixel identifier.
 * Stable across the order's lifetime; never reused after eviction.
 */
public record Order(
        UUID id,
        String productId,
        TransactionType.Side side,
        boolean priceExact,
        double pricePerItem,
        int originalAmount,
        int filledAmount,
        int claimedAmount,
        OrderSlotPosition slotPosition,
        OrderStatus status,
        long placedAt,
        long lastUpdatedAt,
        OrderAttribution attribution,
        Optional<Long> expiresAt
) implements PriceQuantity {
    public static final Codec<Order> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(Order::id),
            Codec.STRING.fieldOf("productId").forGetter(Order::productId),
            TransactionType.Side.CODEC.fieldOf("side").forGetter(Order::side),
            Codec.BOOL.fieldOf("priceExact").forGetter(Order::priceExact),
            Codec.DOUBLE.fieldOf("pricePerItem").forGetter(Order::pricePerItem),
            Codec.INT.fieldOf("originalAmount").forGetter(Order::originalAmount),
            Codec.INT.fieldOf("filledAmount").forGetter(Order::filledAmount),
            Codec.INT.fieldOf("claimedAmount").forGetter(Order::claimedAmount),
            OrderSlotPosition.CODEC.fieldOf("slotPosition").forGetter(Order::slotPosition),
            OrderStatus.CODEC.fieldOf("status").forGetter(Order::status),
            Codec.LONG.fieldOf("placedAt").forGetter(Order::placedAt),
            Codec.LONG.fieldOf("lastUpdatedAt").forGetter(Order::lastUpdatedAt),
            OrderAttribution.CODEC.fieldOf("attribution").forGetter(Order::attribution),
            Codec.LONG.optionalFieldOf("expiresAt").forGetter(Order::expiresAt)
    ).apply(instance, Order::new));

    // ── Computed quantities ───────────────────────────────────────────────────

    /** Units not yet filled. */
    public int unfilledAmount() {
        return originalAmount - filledAmount;
    }

    /** Units (BUY) or coin batches (SELL, batch = price per unit) that have been filled but not yet claimed. */
    public int unclaimedFilled() {
        return filledAmount - claimedAmount;
    }

    /** Returns the known expiry, or {@code placedAt + 7 days} if none has been observed yet. */
    public long effectiveExpiresAt() {
        return expiresAt.orElse(placedAt + PageOrderParser.ORDER_EXPIRY_MS);
    }

    /** {@link PriceQuantity}'s volume */
    @Override
    public int volume() {
        return originalAmount;
    }

    /** {@link PriceQuantity}'s flag */
    @Override
    public boolean priceIsExact() {
        return priceExact || PriceQuantity.super.priceIsExact();
    }

    // ── Position predicates ───────────────────────────────────────────────────

    /**
     * {@code true} when the order is currently rendered on the Orders page
     * ({@link OrderSlotPosition.OnScreen}).
     */
    public boolean isVisible() {
        return slotPosition.isVisible();
    }

    // ── Fill priority ─────────────────────────────────────────────────────────

    /**
     * Returns a comparator that orders tracked orders by fill priority for the given side.
     *
     * <p>Fill priority (market semantics):
     * <ul>
     *   <li>BUY:  higher price fills first (price descending), then FIFO ({@code placedAt} ascending).</li>
     *   <li>SELL: lower  price fills first (price ascending),  then FIFO ({@code placedAt} ascending).</li>
     * </ul>
     *
     * @param type the price type of all orders being compared
     */
    public static Comparator<Order> byFillPriority(PriceType type) {
        Comparator<Order> byPrice = Comparator.comparingDouble(Order::pricePerItem);

        if (type.higherIsBetter()) {
            byPrice = byPrice.reversed(); // higher price fills first
        }

        return byPrice.thenComparingLong(Order::placedAt); // FIFO tiebreak
    }

    // ── Status predicates ─────────────────────────────────────────────────────

    /** Matches orders for {@code productId}, regardless of side. */
    public static Predicate<Order> forProduct(@NotNull String productId) {
        return order -> order.productId().equals(productId);
    }

    /** @see #forProduct(String) — narrowed to orders on {@code side}. */
    public static Predicate<Order> forProduct(@NotNull String productId, @NotNull TransactionType.Side side) {
        return forProduct(productId).and(order -> order.side() == side);
    }
    /** Matches orders resting at {@code price}, honoring each order's own exactness. */
    public static Predicate<Order> forPrice(double price) {
        return order -> order.priceIsExact()
                ? Double.compare(order.pricePerItem(), price) == 0
                : order.isPriceSimilarTo(price);
    }

    /** Matches orders resting at {@code reference}'s price, honoring each order's own exactness. */
    public static Predicate<Order> forPrice(@NotNull PriceQuantity reference) {
        return order -> order.priceIsExact()
                ? Double.compare(order.pricePerItem(), reference.pricePerItem()) == 0
                : forPriceSimilarTo(reference).test(order);
    }

    /** Matches orders resting within tolerance of {@code price} — see {@link PriceQuantity#isPriceSimilarTo}. */
    public static Predicate<Order> forPriceSimilarTo(double price) {
        return PriceQuantity.similarTo(price);
    }

    public static Predicate<Order> forPriceSimilarTo(@NotNull PriceQuantity reference) {
        return PriceQuantity.similarTo(reference);
    }

    public boolean isBuyOrder() {
        return side == TransactionType.Side.BUY;
    }

    public boolean isSellOffer() {
        return side == TransactionType.Side.SELL;
    }

    public boolean hasExpired() {
        return status instanceof OrderStatus.Expired;
    }

    /**
     * {@code true} when the order still has a live position resting in the book —
     * {@link OrderStatus.Set} or {@link OrderStatus.Partial}. Always {@code false}
     * once expired, regardless of what it interrupted — an expired position has
     * already left the book.
     */
    public boolean isOpen() {
        return status instanceof OrderStatus.BookState;
    }

    /**
     * {@code true} when every unit has filled — delegates to {@link OrderStatus#isFilled()},
     * which looks through an {@link OrderStatus.Expired} to whatever it interrupted.
     */
    public boolean isFilled() {
        return status.isFilled();
    }

    /**
     * {@code true} when the order still has unfilled volume to return and no unclaimed
     * fill outstanding. Unlike {@link #isOpen()}, this looks through
     * {@link OrderStatus.Expired} via {@link OrderStatus#hasUnfilledRemainder()} — an
     * expired order that interrupted a partial fill is still cancellable once its
     * filled portion is claimed, exactly like a still-open one.
     */
    public boolean isCancellable() {
        return status.hasUnfilledRemainder() && !isClaimable();
    }

    /**
     * {@code true} when this BUY order can be flipped.
     *
     * <p>Conditions (all must hold):
     * <ol>
     *   <li>Side is BUY.</li>
     *   <li>Status is {@link OrderStatus.Filled}.</li>
     *   <li>There is at least one unclaimed unit ({@link #unclaimedFilled()} > 0).</li>
     * </ol>
     */
    public boolean isFlippable() {
        return isBuyOrder() && isFilled() && isClaimable();
    }

    /**
     * {@code true} when the order has filled volume not yet retrieved.
     * Requires the order to be visible — off-screen and unanchored orders cannot
     * be interacted with from the UI.
     */
    public boolean isClaimable() {
        return isVisible() && unclaimedFilled() > 0;
    }

    /** {@code true} when some action — fill, claim, or cancellation — could still happen to this order. */
    public boolean isActionable() {
        return status instanceof OrderStatus.Actionable;
    }

    /** {@code true} when this order is fully resolved with the player — nothing further can happen to it. */
    public boolean isTerminal() {
        return status instanceof OrderStatus.Terminal;
    }

    /**
     * {@code true} when clicking this order opens the Order Options screen.
     */
    public boolean canOpenOptions() {
        return isCancellable() || isFlippable();
    }

    /**
     * Whether {@code observedFill} — a fresh, not-yet-reconciled screen or chat
     * reading — is a value this order's OWN filledAmount could legally have advanced
     * to, honoring the two hard rules of the fill state machine: fill only ever
     * advances, never regresses (within {@code tolerance}, absorbing a k/M display
     * abbreviation's own under-report bound); and once this order is no longer
     * {@link #isOpen()} — {@link OrderStatus.Filled}, or {@link OrderStatus.Expired}
     * wrapping anything — its filledAmount is frozen for good, so a reading showing
     * MORE than what's already stored can never be legal past that point.
     */
    public boolean canHaveAdvancedTo(int observedFill, int tolerance) {
        boolean notRegressed = observedFill >= filledAmount - tolerance;
        boolean frozenRespected = isOpen() || observedFill <= filledAmount;

        return notRegressed && frozenRespected;
    }

    /** @see #canHaveAdvancedTo(int, int) — defaults tolerance to {@link PageOrderParser#FILL_TRUNCATION_MAX}. */
    public boolean canHaveAdvancedTo(int observedFill) {
        return canHaveAdvancedTo(observedFill, PageOrderParser.FILL_TRUNCATION_MAX);
    }

    // ── Market position ───────────────────────────────────────────────────────

    /**
     * Queries the live market to build a context for the given order.
     *
     * <p>Operates entirely through {@link BazaarDataQuery}, keeping this context
     * decoupled from the book's internal layered structure. Performs the position
     * and order-count lookups exactly once; call {@link PricingPosition.PositionContext#classify(boolean)} on the
     * result as many times as needed at no further cost.
     *
     * @param userOrders snapshot of the player's tracked orders
     *
     * @return the raw market context, or empty if the product has no book data
     */
    public Optional<PricingPosition.PositionContext> positionContext(List<Order> userOrders) {
        var transaction = TransactionType.of(this.side(), TransactionType.Method.ORDER);

        var aheadOpt = BazaarDataQuery.positionOf(this.productId(), transaction, this.pricePerItem());
        if (aheadOpt.isEmpty()) return Optional.empty();

        int ahead = aheadOpt.getAsInt();
        if (ahead > 0) return Optional.of(new PricingPosition.PositionContext(ahead, 0, 0));

        var poolOpt = BazaarDataQuery.orderCount(this.productId(), transaction, this.pricePerItem());
        if (poolOpt.isEmpty()) return Optional.empty();

        int ownAtPrice = (int) userOrders.stream()
                .filter(Order::isOpen)
                .filter(Order.forProduct(this.productId(), this.side()))
                .filter(Order.forPrice(this.pricePerItem()))
                .count();

        return Optional.of(new PricingPosition.PositionContext(ahead, poolOpt.getAsInt(), ownAtPrice));
    }


    /**
     * Evaluates the competitive standing of a tracked order against the current market.
     * Intended for <b>status display only</b> — not for price calculation.
     *
     * @param userOrders caller-supplied snapshot of all tracked orders;
     *                   used to count self-owned positions at this price level
     * @param selfOutbid when {@code true}, own orders at this price level are counted as
     *                   external competition; when {@code false}, they are excluded so a
     *                   position occupied solely by the player's own volume reports COMPETITIVE
     *
     * @return the evaluated standing, or empty if the product has no book data
     */
    public Optional<PricingPosition> position(@NotNull List<Order> userOrders, boolean selfOutbid) {
        return positionContext(userOrders).map(ctx -> ctx.classify(selfOutbid));
    }

    // ── Mutation-returning helpers ─────────────────────────────────────────────

    /** Returns a copy with a different {@code id}. */
    public Order withId(UUID id) {
        return new Order(id, productId, side, priceExact, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slotPosition, status, placedAt, lastUpdatedAt, attribution, expiresAt);
    }

    /**
     * Returns a copy with a refined {@code expiresAt} stamp. Does not advance
     * {@code lastUpdatedAt} — a metadata correction, not a state transition.
     */
    public Order withExpiresAt(long timestamp) {
        return new Order(id, productId, side, priceExact, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slotPosition, status,
                placedAt, lastUpdatedAt, attribution, Optional.of(timestamp));
    }

    /**
     * Returns a copy with a corrected {@code placedAt}. Does not advance
     * {@code lastUpdatedAt} — a metadata correction, not a state transition,
     * matching {@link #withExpiresAt}'s own reasoning.
     */
    public Order withPlacedAt(long placedAt) {
        return new Order(id, productId, side, priceExact, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slotPosition, status,
                placedAt, lastUpdatedAt, attribution, expiresAt);
    }

    /**
     * Returns a copy with {@code slotPosition} updated per the value of {@code slot}.
     *
     * <p>{@code lastUpdatedAt} is intentionally preserved — slot reanchoring is
     * positional bookkeeping, not a data event, and must not affect eviction logic.
     */
    public Order reanchored(OrderSlotPosition slot) {
        return new Order(id, productId, side, priceExact, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slot, status, placedAt, lastUpdatedAt, attribution, expiresAt);
    }

    /**
     * Returns a cancelled copy. Requires the order to currently be
     * {@link OrderStatus.Actionable} — open on the book, or {@link OrderStatus.Expired}
     * awaiting the player's manual cancel via Options — never
     * already {@link OrderStatus.Terminal}, since a {@code Cancelled} or {@code Claimed}
     * order has nothing left to cancel.
     */
    public Order cancelled(BazaarDataOrigin origin) throws IllegalStateException {
        if (!(status instanceof OrderStatus.Actionable)) {
            throw new IllegalStateException("Cannot cancel an order that's already terminal: " + describe());
        }

        return new Order(id, productId, side, priceExact, pricePerItem, originalAmount,
                filledAmount, claimedAmount, new OrderSlotPosition.Terminal(),
                new OrderStatus.Cancelled(origin.timestamp()), placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy transitioned to {@link OrderStatus.Expired}, wrapping the
     * order's current {@link OrderStatus.FillState} shape — Set, Partial, OR Filled.
     */
    public Order expired(BazaarDataOrigin origin) throws IllegalStateException {
        var previous = status.fillState().orElseThrow(() -> new IllegalStateException("Cannot expire an order that isn't currently on the market: " + describe()));

        return new Order(id, productId, side, priceExact, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slotPosition,
                new OrderStatus.Expired(origin.timestamp(), previous),
                placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy with {@code amount} added to {@code filledAmount}, advancing
     * the wrapped {@link OrderStatus.FillState} to {@link OrderStatus.Filled} once the
     * total meets {@code originalAmount}, or to {@link OrderStatus.Partial} otherwise —
     * preserving {@code firstFilledAt} across every fill after the first.
     */
    public Order withFill(int amount, BazaarDataOrigin origin) throws IllegalStateException {
        int total = Math.min(filledAmount + amount, originalAmount);
        OrderStatus newStatus = status.reconciledWith(filledAmount, total, originalAmount, hasExpired(), origin.timestamp());

        return new Order(id, productId, side, priceExact, pricePerItem, originalAmount,
                total, claimedAmount, slotPosition, newStatus, placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy with {@code amount} added to {@code claimedAmount}.
     *
     * <p>Transitions to terminal {@link OrderStatus.Claimed} once
     * {@link OrderStatus#hasUnfilledRemainder()} is {@code false} — nothing was ever
     * left to fill — and every filled unit has now been claimed.
     */
    public Order withClaim(int amount, BazaarDataOrigin origin) {
        int claim = Math.min(claimedAmount + amount, filledAmount);

        boolean fullyResolved = !status.hasUnfilledRemainder() && claim >= filledAmount;

        OrderStatus newStatus = fullyResolved ? new OrderStatus.Claimed(origin.timestamp()) : status;
        OrderSlotPosition newPos = fullyResolved ? new OrderSlotPosition.Terminal() : slotPosition;

        return new Order(id, productId, side, priceExact, pricePerItem, originalAmount,
                filledAmount, claim, newPos, newStatus,
                placedAt, origin.timestamp(), attribution, expiresAt);
    }

    // ── Display ───────────────────────────────────────────────────────────────

    public String describe() {
        return "%s %s %d/%dx@%.4f%s %s %s | %s".formatted(
                productId(), side(), filledAmount(), originalAmount(),
                pricePerItem(), priceExact() ? "" : "~", slotPosition().describe(), attribution().describe(),
                status().describe());
    }
}