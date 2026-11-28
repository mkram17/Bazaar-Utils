package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataQuery;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Immutable snapshot of a player's tracked Bazaar order.
 *
 * <p>{@code id} is a random {@link UUID} assigned at synthesis time — not a Hypixel identifier.
 * Stable across the order's lifetime; never reused after eviction.
 *
 * <p>{@code price} is a {@link PriceInfo} carrying both the per-unit price and its provenance
 * precision ({@link PriceInfo.Exact} or {@link PriceInfo.Implied}). Exact prices compare
 * strictly; implied prices compare within their combined tolerance bands via
 * {@link PriceInfo#matches}.
 */
public record Order(
        @NotNull UUID id,
        @NotNull String productId,
        @NotNull TransactionType.Side side,
        @NotNull PriceInfo price,
        int originalAmount,
        int filledAmount,
        int claimedAmount,
        @NotNull OrderSlotPosition slotPosition,
        @NotNull OrderStatus status,
        long placedAt,
        long lastUpdatedAt,
        @NotNull OrderAttribution attribution,
        @NotNull Optional<Long> expiresAt
) {
    /** Standard Hypixel Bazaar order lifetime: 7 days in milliseconds. */
    public static final long ORDER_EXPIRY_MS = 7L * 24 * 3_600_000L;

    /**
     * Worst-case under-report from Hypixel's k-abbreviation of a filled count
     * (e.g. {@code 1.5k} representing up to {@code 1,599} units).
     */
    public static final int FILL_TRUNCATION_MAX = 100;

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            str -> {
                try {
                    return DataResult.success(UUID.fromString(str));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Invalid Order UUID: " + str);
                }
            },
            UUID::toString
    );

    public static final Codec<Order> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("id").forGetter(Order::id),
            Codec.STRING.fieldOf("productId").forGetter(Order::productId),
            TransactionType.Side.CODEC.fieldOf("side").forGetter(Order::side),
            PriceInfo.CODEC.fieldOf("price").forGetter(Order::price),
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

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(slotPosition, "slotPosition");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(attribution, "attribution");
        Objects.requireNonNull(expiresAt, "expiresAt");

        if (originalAmount <= 0) {
            throw new IllegalArgumentException("Order originalAmount must be positive: " + originalAmount);
        }
        if (filledAmount < 0 || filledAmount > originalAmount) {
            throw new IllegalArgumentException(
                    "Order filledAmount (%d) must be in [0, %d]".formatted(filledAmount, originalAmount));
        }
        if (claimedAmount < 0 || claimedAmount > filledAmount) {
            throw new IllegalArgumentException(
                    "Order claimedAmount (%d) must be in [0, %d]".formatted(claimedAmount, filledAmount));
        }
        if ((status instanceof OrderStatus.Terminal) != (slotPosition instanceof OrderSlotPosition.Terminal)) {
            throw new IllegalArgumentException(
                    "Order status (%s) and slotPosition (%s) must agree on terminality"
                            .formatted(status.describe(), slotPosition.describe()));
        }
    }

    // ── Price accessors ───────────────────────────────────────────────────────

    /** Per-unit price in coins. */
    public double pricePerItem() {
        return price.pricePerItem();
    }

    /** {@code true} when {@link #price()} has zero tolerance (read directly or below the rounding fold). */
    public boolean isPriceExact() {
        return price.isExact();
    }

    /** Per-unit tolerance band for fuzzy matching ({@code 0.0} when exact). */
    public double tolerance() {
        return price.tolerance();
    }

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
        return expiresAt.orElse(placedAt + ORDER_EXPIRY_MS);
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
     * Returns a comparator ordering orders resting on {@code type}'s side of the book by the
     * priority the market fills them: best price first — highest when
     * {@link PriceType#higherIsBetter()}, lowest otherwise — then earliest {@code placedAt}.
     *
     * <p>{@code type} names the side the orders rest on, not the player's intent: buy orders rest
     * on the {@code INSTASELL} (bid) side, so pass {@link TransactionType#getPriceType()} of the
     * order transaction rather than reasoning from "buy" or "sell".
     */
    public static Comparator<Order> byFillPriority(PriceType type) {
        Comparator<Order> byPrice = Comparator.comparingDouble(Order::pricePerItem);

        if (type.higherIsBetter()) {
            byPrice = byPrice.reversed(); // higher price fills first
        }

        return byPrice.thenComparingLong(Order::placedAt); // FIFO tiebreak
    }

    // ── Status & filter predicates ────────────────────────────────────────────

    /** Matches orders for {@code productId}, regardless of side. */
    public static Predicate<Order> forProduct(@NotNull String productId) {
        return order -> order.productId().equals(productId);
    }

    /** @see #forProduct(String) — narrowed to orders on {@code side}. */
    public static Predicate<Order> forProduct(@NotNull String productId, @NotNull TransactionType.Side side) {
        return forProduct(productId).and(order -> order.side() == side);
    }

    /** Matches orders with exact IEEE equality on {@code pricePerItem}. */
    public static Predicate<Order> forExactPrice(double price) {
        return order -> Double.compare(order.pricePerItem(), price) == 0;
    }

    /** Matches orders resting at {@code price} using the order's {@link PriceInfo#matches(double)} precision. */
    public static Predicate<Order> forPrice(double price) {
        return order -> order.price().matches(price);
    }

    /** Matches orders resting at {@code reference}'s price using symmetric {@link PriceInfo#matches(PriceInfo)} precision. */
    public static Predicate<Order> forPrice(@NotNull PriceInfo reference) {
        return order -> order.price().matches(reference);
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
     * {@code true} when filled volume remains unclaimed, independent of whether the order
     * is currently visible on-screen.
     */
    public boolean hasUnclaimedFill() {
        return unclaimedFilled() > 0;
    }

    /**
     * {@code true} when the order still has unfilled volume to return and no unclaimed
     * fill outstanding. Unlike {@link #isOpen()}, this looks through
     * {@link OrderStatus.Expired} via {@link OrderStatus#hasUnfilledRemainder()} — an
     * expired order that interrupted a partial fill is still cancellable once its
     * filled portion is claimed, exactly like a still-open one.
     */
    public boolean isCancellable() {
        return status.hasUnfilledRemainder() && !hasUnclaimedFill();
    }

    /**
     * {@code true} when this BUY order can be flipped.
     *
     * <p>Conditions (all must hold):
     * <ol>
     *   <li>Side is BUY.</li>
     *   <li>Status is {@link OrderStatus.Filled} (or {@link OrderStatus.Expired} wrapping it).</li>
     *   <li>There is at least one unclaimed unit ({@link #hasUnclaimedFill()}).</li>
     * </ol>
     */
    public boolean isFlippable() {
        return isBuyOrder() && isFilled() && hasUnclaimedFill();
    }

    /**
     * {@code true} when the order has filled volume not yet retrieved AND is currently
     * rendered on the Orders page — off-screen orders cannot be clicked from the UI.
     */
    public boolean isClaimable() {
        return isVisible() && hasUnclaimedFill();
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
     * abbreviation's own under-report bound); and once this order has no unfilled
     * remainder ({@link OrderStatus.Filled}, an {@link OrderStatus.Expired} wrapping
     * {@code Filled}, or {@link OrderStatus.Terminal}) its filledAmount is frozen for
     * good. A locally-expired order that interrupted {@code Set} or {@code Partial} may
     * still reconcile pre-expiry fills observed on screen.
     */
    public boolean canHaveAdvancedTo(int observedFill, int tolerance) {
        boolean notRegressed = observedFill >= filledAmount - tolerance;
        boolean frozenRespected = status.hasUnfilledRemainder() || observedFill <= filledAmount;

        return notRegressed && frozenRespected;
    }

    /** @see #canHaveAdvancedTo(int, int) — defaults tolerance to {@link #FILL_TRUNCATION_MAX}. */
    public boolean canHaveAdvancedTo(int observedFill) {
        return canHaveAdvancedTo(observedFill, FILL_TRUNCATION_MAX);
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
                .filter(Order.forExactPrice(this.pricePerItem()))
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
        return new Order(id, productId, side, price, originalAmount,
                filledAmount, claimedAmount, slotPosition, status, placedAt, lastUpdatedAt, attribution, expiresAt);
    }

    /**
     * Returns a copy with a refined {@code expiresAt} stamp. Does not advance
     * {@code lastUpdatedAt} — a metadata correction, not a state transition.
     */
    public Order withExpiresAt(long timestamp) {
        return new Order(id, productId, side, price, originalAmount,
                filledAmount, claimedAmount, slotPosition, status,
                placedAt, lastUpdatedAt, attribution, Optional.of(timestamp));
    }

    /**
     * Returns a copy with a corrected {@code placedAt}. Does not advance
     * {@code lastUpdatedAt} — a metadata correction, not a state transition,
     * matching {@link #withExpiresAt}'s own reasoning.
     */
    public Order withPlacedAt(long placedAt) {
        return new Order(id, productId, side, price, originalAmount,
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
        return new Order(id, productId, side, price, originalAmount,
                filledAmount, claimedAmount, slot, status, placedAt, lastUpdatedAt, attribution, expiresAt);
    }

    /**
     * Returns a cancelled copy, with {@code slotPosition} moved to
     * {@link OrderSlotPosition.Terminal}.
     *
     * @throws IllegalStateException if the order is already {@link OrderStatus.Terminal} —
     *         a {@code Cancelled} or {@code Claimed} order has nothing left to cancel. An
     *         {@link OrderStatus.Expired} order is still cancellable: that is how the
     *         player recovers its unfilled escrow via Options.
     */
    public Order cancelled(BazaarDataOrigin origin) throws IllegalStateException {
        if (!(status instanceof OrderStatus.Actionable)) {
            throw new IllegalStateException("Cannot cancel an order that's already terminal: " + describe());
        }

        return new Order(id, productId, side, price, originalAmount,
                filledAmount, claimedAmount, OrderSlotPosition.Terminal.INSTANCE,
                new OrderStatus.Cancelled(origin.timestamp()), placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy transitioned to {@link OrderStatus.Expired}, wrapping the order's
     * current {@link OrderStatus.FillState} — {@code Set}, {@code Partial}, or
     * {@code Filled}. No-ops (returning {@code this}) if the order is already
     * {@link OrderStatus.Expired}, preserving its original {@code expiredAt}.
     *
     * @throws IllegalStateException if the order is already {@link OrderStatus.Terminal}
     */
    public Order expired(BazaarDataOrigin origin) throws IllegalStateException {
        if (status instanceof OrderStatus.Expired) return this;

        var previous = status.fillState().orElseThrow(() -> new IllegalStateException("Cannot expire an order that isn't currently on the market: " + describe()));

        return new Order(id, productId, side, price, originalAmount,
                filledAmount, claimedAmount, slotPosition,
                new OrderStatus.Expired(origin.timestamp(), previous),
                placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy with {@code amount} added to {@code filledAmount}, clamped to
     * {@code originalAmount}, and its status advanced to match via
     * {@link OrderStatus#reconciledWith}. Non-positive amounts or calls on an already-filled
     * order return {@code this} unchanged. An expired order stays expired: its interrupted
     * fill state advances while its original {@code expiredAt} is kept.
     *
     * @throws IllegalStateException if the order is already {@link OrderStatus.Terminal}
     */
    public Order withFill(int amount, BazaarDataOrigin origin) throws IllegalStateException {
        if (!(status instanceof OrderStatus.Actionable)) {
            throw new IllegalStateException("Cannot fill an order that's already terminal: " + describe());
        }
        if (amount <= 0) return this;

        int total = (int) Math.min((long) filledAmount + amount, originalAmount);
        if (total == filledAmount) return this;

        OrderStatus newStatus = status.reconciledWith(filledAmount, total, originalAmount, hasExpired(), origin.timestamp());

        return new Order(id, productId, side, price, originalAmount,
                total, claimedAmount, slotPosition, newStatus, placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy with {@code amount} added to {@code claimedAmount}, capped at
     * {@code filledAmount}.
     *
     * <p>Once {@link OrderStatus#hasUnfilledRemainder()} is {@code false} — nothing was
     * ever left to fill — and every filled unit has now been claimed, the status becomes
     * terminal {@link OrderStatus.Claimed} and {@code slotPosition} moves to
     * {@link OrderSlotPosition.Terminal}.
     *
     * @throws IllegalStateException    if the order is already {@link OrderStatus.Terminal}
     * @throws IllegalArgumentException if {@code amount} is negative
     */
    public Order withClaim(int amount, BazaarDataOrigin origin) {
        if (!(status instanceof OrderStatus.Actionable)) {
            throw new IllegalStateException("Cannot claim an order that's already terminal: " + describe());
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Claim amount must be non-negative: " + amount);
        }

        int claim = (int) Math.min((long) claimedAmount + amount, filledAmount);
        boolean fullyResolved = !status.hasUnfilledRemainder() && claim >= filledAmount;

        OrderStatus newStatus = fullyResolved ? new OrderStatus.Claimed(origin.timestamp()) : status;
        OrderSlotPosition newPos = fullyResolved ? OrderSlotPosition.Terminal.INSTANCE : slotPosition;

        return new Order(id, productId, side, price, originalAmount,
                filledAmount, claim, newPos, newStatus,
                placedAt, origin.timestamp(), attribution, expiresAt);
    }

    // ── Display ───────────────────────────────────────────────────────────────

    public String describe() {
        return "%s %s %d/%dx@%.4f%s %s %s | %s".formatted(
                productId(), side(), filledAmount(), originalAmount(),
                pricePerItem(), isPriceExact() ? "" : "~", slotPosition().describe(), attribution().describe(),
                status().describe());
    }
}
