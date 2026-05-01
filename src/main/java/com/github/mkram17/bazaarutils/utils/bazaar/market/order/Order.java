package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.MarketQuery;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

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
        double pricePerItem,
        int originalAmount,
        int filledAmount,
        int claimedAmount,
        OrderSlotPosition slotPosition,
        OrderStatus status,
        long placedAt,
        long lastUpdatedAt,
        OrderAttribution attribution,
        @Nullable Long expiresAt
) {
    public static final Codec<Order> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(Order::id),
            Codec.STRING.fieldOf("productId").forGetter(Order::productId),
            TransactionType.Side.CODEC.fieldOf("side").forGetter(Order::side),
            Codec.DOUBLE.fieldOf("pricePerItem").forGetter(Order::pricePerItem),
            Codec.INT.fieldOf("originalAmount").forGetter(Order::originalAmount),
            Codec.INT.fieldOf("filledAmount").forGetter(Order::filledAmount),
            Codec.INT.fieldOf("claimedAmount").forGetter(Order::claimedAmount),
            OrderSlotPosition.CODEC.fieldOf("slotPosition").forGetter(Order::slotPosition),
            OrderStatus.CODEC.fieldOf("status").forGetter(Order::status),
            Codec.LONG.fieldOf("placedAt").forGetter(Order::placedAt),
            Codec.LONG.fieldOf("lastUpdatedAt").forGetter(Order::lastUpdatedAt),
            OrderAttribution.CODEC.fieldOf("attribution").forGetter(Order::attribution),
            Codec.LONG.optionalFieldOf("expiresAt", null).forGetter(Order::expiresAt)
    ).apply(instance, Order::new));

    // ── Computed quantities ───────────────────────────────────────────────────

    /** Units not yet filled. */
    public int unfilledAmount() {
        return originalAmount - filledAmount;
    }

    /** Units (BUY) or coin batches (SELL) that have been filled but not yet claimed. */
    public int unclaimedFilled() {
        return filledAmount - claimedAmount;
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
     * @param side the side of all orders being compared
     */
    public static Comparator<Order> byFillPriority(TransactionType.Side side) {
        Comparator<Order> byPrice = switch (side) {
            case BUY -> Comparator.comparingDouble(Order::pricePerItem).reversed(); // higher bid fills first
            case SELL -> Comparator.comparingDouble(Order::pricePerItem); // lower ask fills first
        };

        return byPrice.thenComparingLong(Order::placedAt); // FIFO tiebreak
    }

    // ── Status predicates ─────────────────────────────────────────────────────

    public static Predicate<Order> forProduct(String productId, TransactionType.Side side) {
        return order -> order.productId().equals(productId) && order.side() == side;
    }

    public boolean isBuyOrder() {
        return side == TransactionType.Side.BUY;
    }

    public boolean isSellOffer() {
        return side == TransactionType.Side.SELL;
    }

    public boolean isExpired() {
        return status instanceof OrderStatus.Expired;
    }

    /**
     * The NonTerminal status this order's fill/claim semantics are currently governed
     * by — {@code status} itself when it's already NonTerminal, or the wrapped
     * {@code previousStatus} when {@code status} is Expired. Empty for Cancelled/Claimed,
     * which are fully resolved and carry no such fallback.
     */
    private Optional<OrderStatus.NonTerminal> effectiveNonTerminal() {
        return switch (status) {
            case OrderStatus.NonTerminal nonTerminal -> Optional.of(nonTerminal);
            case OrderStatus.Expired(var ignored, var previous) -> Optional.of(previous);
            case OrderStatus.Cancelled ignored -> Optional.empty();
            case OrderStatus.Claimed ignored -> Optional.empty();
        };
    }

    /**
     * {@code true} when this order still has an open market position — unfilled,
     * partially filled, or fully filled but not yet claimed/flipped. {@code false}
     * for Expired, deliberately: the position itself was withdrawn, even though the
     * order still needs player action and is NOT terminal. Not {@code !isTerminal()}
     * — that would wrongly include Expired.
     */
    public boolean isLive() {
        return status instanceof OrderStatus.NonTerminal;
    }

    /**
     * {@code true} when the order is still open on the Bazaar —
     * i.e. it can still be matched, partially filled, or cancelled.
     *
     * <p>Does NOT include {@link OrderStatus.Filled} — a filled order is complete and
     * no longer open, but is not terminal either (it still requires a claim or flip).
     */
    public boolean isActive() {
        return status instanceof OrderStatus.Set || status instanceof OrderStatus.Partial;
    }

    /** {@code true} when the order is fully filled, not yet claimed or flipped. */
    public boolean isFilled() {
        return status.effectiveNonTerminal().filter(ns -> ns instanceof OrderStatus.Filled).isPresent();
    }

    /**
     * {@code true} when this order can be cancelled via the Order Options menu.
     *
     * <p>Deliberately not {@code isActive() && !isClaimable()} — {@link #isActive}
     * stays narrow on purpose (still resting on the live book; unconditionally false
     * once expired) and would wrongly exclude an already-expired Set/Partial-origin
     * order from ever being cancellable. This is genuinely a different question: not
     * "is this order still on the book," but "was — or, through an Expired wrapper,
     * still effectively is — this order in a Set/Partial fill-state, with nothing
     * currently unclaimed." A Set/Partial-origin expired order satisfies that the
     * same way a live one does: claim whatever's outstanding, then cancel to recover
     * the unfilled remainder's escrow.
     */
    public boolean isCancellable() {
        return status.effectiveNonTerminal()
                .filter(ns -> ns instanceof OrderStatus.Set || ns instanceof OrderStatus.Partial)
                .isPresent()
                && !isClaimable();
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

    /**
     * {@code true} when the order cannot transition to any further state.
     * Only {@link OrderStatus.Claimed} and {@link OrderStatus.Cancelled} are terminal.
     * {@link OrderStatus.Filled} and {@link OrderStatus.Expired} are not terminal — both
     * still require player interaction before leaving the Orders screen.
     */
    public boolean isTerminal() {
        return status instanceof OrderStatus.Claimed || status instanceof OrderStatus.Cancelled;
    }

    /**
     * {@code true} when clicking this order opens the Order Options screen.
     *
     * <p>No standalone Expired clause anymore — now that {@link #isCancellable} and
     * {@link #isFlippable} both correctly see through an Expired wrapper, every case
     * that genuinely needs Options is already one of the two. The remaining expired
     * shape neither covers — Partial-origin, still holding unclaimed fill — is
     * deliberately NOT selectable yet: claiming comes first, a separate action
     * governed by {@link #isClaimable}; Options/cancel only becomes relevant once
     * that's exhausted.
     */
    public boolean isSelectable() {
        return isCancellable() || isFlippable();
    }

    // ── Market position ───────────────────────────────────────────────────────

    /**
     * Returns the raw market facts behind this order's standing, or empty
     * when the product has no book data at all. Resolved entirely through
     * {@link MarketQuery} rather than the registry directly, so this record
     * stays ignorant of the book's own layered structure.
     *
     * <p>Performs the position and order-count lookups once; call
     * {@link PositionContext#classify(boolean)} on the result as many times
     * as needed at no further cost.
     */
    public Optional<PositionContext> positionContext(List<Order> userOrders) {
        var transaction = TransactionType.of(side, TransactionType.Method.ORDER);

        var aheadOpt = MarketQuery.positionOf(productId, transaction, pricePerItem);
        if (aheadOpt.isEmpty()) return Optional.empty();

        int ahead = aheadOpt.getAsInt();
        if (ahead > 0) return Optional.of(new PositionContext(ahead, 0, 0));

        var poolOpt = MarketQuery.orderCount(productId, transaction, pricePerItem);
        if (poolOpt.isEmpty()) return Optional.empty();

        int ownAtPrice = (int) userOrders.stream()
                .filter(Order.forProduct(productId, side))
                .filter(Order::isActive)
                .filter(order -> order.pricePerItem() == pricePerItem)
                .count();

        return Optional.of(new PositionContext(ahead, poolOpt.getAsInt(), ownAtPrice));
    }

    /**
     * Reports the competitive standing of this order against the current market.
     * Intended for <b>status display only</b> — not for price calculation.
     *
     * @param userOrders  caller-supplied snapshot of all tracked orders;
     *                    used to count self-owned positions at this price level
     * @param selfOutbid  when {@code true}, own orders at this price level are counted as
     *                    external competition; when {@code false} they are excluded so a
     *                    position occupied solely by your own volume reports COMPETITIVE
     */
    public Optional<PricingPosition> position(List<Order> userOrders, boolean selfOutbid) {
        return positionContext(userOrders).map(ctx -> ctx.classify(selfOutbid));
    }

    /** Returns the best-known expiry epoch ms. Falls back to {@code placedAt + 7 days}. */
    public long effectiveExpiresAt() {
        return expiresAt != null ? expiresAt : placedAt + PageOrderParser.ORDER_EXPIRY_MS;
    }

    // ── Mutation-returning helpers ─────────────────────────────────────────────

    /** Returns a copy with a different {@code id}. Used during order deduplication. */
    public Order withId(UUID newId) {
        return new Order(newId, productId, side, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slotPosition, status, placedAt, lastUpdatedAt, attribution, expiresAt);
    }

    /**
     * Returns a copy with a refined {@code expiresAt} stamp. Does not advance
     * {@code lastUpdatedAt} — this is a metadata correction, not a state transition,
     * and must not affect eviction grace-period logic.
     */
    public Order withExpiresAt(long expiresAt) {
        return new Order(id, productId, side, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slotPosition, status,
                placedAt, lastUpdatedAt, attribution, expiresAt);
    }

    /**
     * Returns a copy with {@code slotPosition} updated.
     *
     * <p>{@code lastUpdatedAt} is intentionally preserved — slot reanchoring is
     * positional bookkeeping, not a data event, and must not affect eviction logic.
     */
    public Order reanchored(OrderSlotPosition newPosition) {
        return new Order(id, productId, side, pricePerItem, originalAmount,
                filledAmount, claimedAmount, newPosition, status, placedAt, lastUpdatedAt, attribution, expiresAt);
    }

    /** Returns a cancelled copy. Only valid when {@link #isCancellable()} is true. */
    public Order cancelled(BazaarDataOrigin origin) {
        return new Order(id, productId, side, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slotPosition,
                new OrderStatus.Cancelled(origin.timestamp()), placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy transitioned to {@link OrderStatus.Expired}, wrapping whichever
     * {@link OrderStatus.NonTerminal} state this order is currently in.
     *
     * <p>Always stamps a fresh {@code expiredAt}. Callers that want to preserve an
     * already-known, more-precise detection timestamp — see
     * {@code OrdersScreenDataSource.reconcileExisting} — are responsible for checking
     * {@link #isExpired()} first and skipping this call rather than relying on it to
     * be idempotent; that's a deliberate choice, not an oversight, matching this
     * codebase's convention of making that kind of decision explicit at the call site
     * rather than folded silently into the primitive.
     */
    public Order expired(BazaarDataOrigin origin) {
        var previous = status.effectiveNonTerminal()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot expire an order whose position is already resolved: " + describe()));

        return new Order(id, productId, side, pricePerItem, originalAmount,
                filledAmount, claimedAmount, slotPosition,
                new OrderStatus.Expired(origin.timestamp(), previous),
                placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy with {@code amount} added to {@code filledAmount}.
     *
     * <p>Transitions to {@link OrderStatus.Filled} when the new total meets or exceeds
     * {@code originalAmount}. Transitions to {@link OrderStatus.Partial} otherwise,
     * preserving {@code firstFilledAt} across subsequent partial fills so the "filling
     * since" timestamp is never lost.
     *
     * <p>{@code filledAmount} is clamped to {@code originalAmount} to absorb any overshoot
     * from races between chat and screen sources.
     */
    public Order withFill(int amount, BazaarDataOrigin origin) {
        if (!(status instanceof OrderStatus.NonTerminal previous)) {
            throw new IllegalStateException(
                    "Cannot advance fill on an order with no open market position: " + describe());
        }

        int total = Math.min(filledAmount + amount, originalAmount);

        OrderStatus newStatus = total >= originalAmount
                ? new OrderStatus.Filled(origin.timestamp())
                : (previous instanceof OrderStatus.Partial existing
                ? new OrderStatus.Partial(existing.firstFilledAt(), origin.timestamp())
                : new OrderStatus.Partial(origin.timestamp(), origin.timestamp()));

        return new Order(id, productId, side, pricePerItem, originalAmount,
                total, claimedAmount, slotPosition, newStatus, placedAt, origin.timestamp(), attribution, expiresAt);
    }

    /**
     * Returns a copy with {@code amount} added to {@code claimedAmount}.
     *
     * <p>Transitions to terminal {@link OrderStatus.Claimed} on a plain numeric fact
     * — {@code filledAmount >= originalAmount} (everything that could ever fill,
     * did) and {@code claim >= filledAmount} (all of it is now claimed) — rather
     * than on which status the order currently wears. That's what actually fixes
     * the gap: the old gate was {@code status instanceof Filled}, so a Filled-origin
     * order that expired before being fully claimed could NEVER reach Claimed at
     * all — its {@code else} branch was literally commented "Expired stays Expired,"
     * permanently. {@code filledAmount >= originalAmount} is true exactly when
     * {@link #withFill} already promoted this order to Filled (bare or, if it later
     * expired, wrapped) — there's no way to reach it while still Set/Partial — so
     * this needs no separate unwrap to confirm "Filled-origin," and it resolves an
     * Expired(Filled) order the instant its claim catches up.
     *
     * <p>For a Set/Partial-origin order — bare or Expired-wrapped — claiming never
     * transitions status at all, by design: an outstanding unfilled remainder still
     * needs an explicit cancel to recover its escrow, a separate action this method
     * doesn't perform. {@link #isCancellable} correctly picks this up on its own
     * once {@code unclaimedFilled() == 0} makes {@link #isClaimable} false.
     */
    public Order withClaim(int amount, BazaarDataOrigin origin) {
        int claim = Math.min(claimedAmount + amount, filledAmount);

        boolean fullyResolved = filledAmount >= originalAmount && claim >= filledAmount;

        OrderStatus newStatus = fullyResolved ? new OrderStatus.Claimed(origin.timestamp()) : status;

        return new Order(id, productId, side, pricePerItem, originalAmount,
                filledAmount, claim, slotPosition, newStatus,
                placedAt, origin.timestamp(), attribution, expiresAt);
    }

    // ── Display ───────────────────────────────────────────────────────────────

    public String describe() {
        return "%s %s %d/%dx@%.4f %s %s | %s".formatted(
                productId(), side(), filledAmount(), originalAmount(),
                pricePerItem(), slotPosition().describe(), attribution().describe(),
                status().describe());
    }

    /**
     * The raw market facts behind an order's standing, before any self-outbid policy is applied.
     *
     * @param ahead         number of orders strictly ahead in the queue (a better price). If > 0,
     *                      the order is outbid outright — self-outbid plays no part in that case.
     * @param totalAtPrice  total order count at this exact price level, everyone included.
     * @param ownAtPrice    how many of those are the player's own active orders.
     */
    public record PositionContext(int ahead, int totalAtPrice, int ownAtPrice) {
        /**
         * Classifies this standing into a {@link PricingPosition}.
         *
         * @param selfOutbid when {@code true}, the player's own orders at this price count as
         *                    external competition; when {@code false} they're excluded, so a price
         *                    level occupied solely by the player's own volume reports COMPETITIVE
         */
        public PricingPosition classify(boolean selfOutbid) {
            if (ahead > 0) return PricingPosition.OUTBID;
            int external = selfOutbid ? Math.max(0, totalAtPrice - 1) : Math.max(0, totalAtPrice - ownAtPrice);
            return external == 0 ? PricingPosition.COMPETITIVE : PricingPosition.MATCHED;
        }
    }
}