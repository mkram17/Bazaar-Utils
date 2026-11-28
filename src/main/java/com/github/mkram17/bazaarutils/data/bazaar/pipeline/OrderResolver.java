package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.HandledOrderAPI;
import com.github.mkram17.bazaarutils.data.TransactionAPI;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Matches a Bazaar chat event's ({@link BazaarChatEvent}) approximate description of an order back
 * to one specific tracked {@link Order}, and picks the best available {@link PriceInfo} to record
 * for a newly placed or flipped one.
 *
 * <p>Fill, claim, and cancel resolution share one shape: {@code for*Candidates} narrows storage to
 * every order the event could describe, {@code select*Target} breaks any tie that remains.
 *
 * <p>Cancellation, claim, and flip resolution all check the screen-selection hint from
 * {@link HandledOrderAPI} first (re-looked up in {@code storage} so stale click snapshots cannot
 * be used) and only then fall back to matching on what the chat event reports. Those fallbacks
 * absorb two kinds of imprecision: unit counts the Orders screen shows k/M-truncated, tolerated up
 * to {@link Order#FILL_TRUNCATION_MAX}, and prices derived from rounded displayed totals, tolerated
 * through {@link PriceInfo}. Fill resolution checks no hint at all: {@link #forFillCandidates}
 * returns every same-size candidate for {@link #selectFillTarget} to disambiguate.
 */
public final class OrderResolver {
    private OrderResolver() {}

    // ── Placement ───────────────────────────────────────────────────────────────

    /**
     * Atomically consumes {@link TransactionAPI}'s pending confirmation and returns its
     * exact price if (and only if) it matches {@code productId}, {@code transaction.getSide()},
     * and {@code volume}. Stale or mismatched pending confirmations are discarded and logged.
     */
    public static @NotNull Optional<PriceInfo.Exact> consumeMatchingPlacement(
            @NotNull String productId,
            @NotNull TransactionType transaction,
            int volume) {
        var pendingOpt = TransactionAPI.consume();
        if (pendingOpt.isEmpty()) return Optional.empty();

        var info = pendingOpt.get();
        if (info.getProductId().equals(productId)
                && info.side() == transaction.getSide()
                && info.getVolume() == volume) {
            return Optional.of(PriceInfo.exact(info.getPricePerItem()));
        }

        Util.logMessage("Discarded mismatched TransactionAPI confirmation — expected=%s %s %dx, pending=%s %s %dx"
                .formatted(productId, transaction.getSide(), volume, info.getProductId(), info.side(), info.getVolume()));

        return Optional.empty();
    }

    /**
     * Returns the {@link PriceInfo} a new placement should be recorded at: the exact price
     * {@link TransactionAPI} captured if it matches {@code productId}/{@code transaction}/{@code volume},
     * or {@link PriceInfo#implied(double, int)} over {@code chatPrice} otherwise.
     */
    public static @NotNull PriceInfo resolveForPlacement(
            @NotNull String productId,
            @NotNull TransactionType transaction,
            int volume,
            double chatPrice) {
        return consumeMatchingPlacement(productId, transaction, volume)
                .map(exact -> (PriceInfo) exact)
                .orElseGet(() -> PriceInfo.implied(chatPrice, volume));
    }

    // ── Fill ────────────────────────────────────────────────────────────────────

    /** Matches on original volume — the only attribute fill resolution is given besides product and side. */
    private static Predicate<Order> filledOrder(int volume) {
        return order -> order.originalAmount() == volume;
    }

    /**
     * Returns every open order on this product and side whose original volume equals
     * {@code volume}. Fill resolution has no price to match on, so several same-size orders can
     * qualify; pass the result to {@link #selectFillTarget} to pick one.
     */
    public static List<Order> forFillCandidates(@NotNull String productId, @NotNull TransactionType transaction, int volume, List<Order> storage) {
        return storage.stream()
                .filter(Order::isOpen)
                .filter(Order.forProduct(productId, transaction.getSide()))
                .filter(filledOrder(volume))
                .toList();
    }

    /**
     * Picks the candidate the market would fill first, per {@link Order#byFillPriority} for
     * {@code transaction}'s price type.
     *
     * @throws NoSuchElementException if {@code candidates} is empty
     */
    public static Order selectFillTarget(@NotNull List<Order> candidates, @NotNull TransactionType transaction) throws NoSuchElementException {
        return candidates.stream()
                .min(Order.byFillPriority(transaction.getPriceType()))
                .orElseThrow();
    }

    // ── Claim ───────────────────────────────────────────────────────────────────

    /**
     * Locates the order a claim advances. The screen-selection hint wins when its live
     * storage entry is actionable on this product and side. Otherwise every actionable order
     * there whose price matches {@code price} per {@link Order#forPrice(PriceInfo)} and whose
     * remaining claim capacity can cover {@code volume} is a candidate.
     */
    public static List<Order> forClaimCandidates(
            @NotNull String productId,
            @NotNull TransactionType transaction,
            @NotNull PriceInfo price,
            int volume,
            List<Order> storage) {
        var clicked = HandledOrderAPI.getForClaim()
                .flatMap(order -> UserOrdersStorage.findById(storage, order.id()))
                .filter(Order::isActionable)
                .filter(Order.forProduct(productId, transaction.getSide()));

        return clicked.map(List::of).orElseGet(() -> storage.stream()
                .filter(Order::isActionable)
                .filter(Order.forProduct(productId, transaction.getSide()))
                .filter(Order.forPrice(price))
                .filter(order -> order.originalAmount() - order.claimedAmount() + Order.FILL_TRUNCATION_MAX >= volume)
                .toList());
    }

    /**
     * Picks the best claim candidate when multiple match: closest {@code unclaimedFilled} to
     * {@code volume}, then closest {@code pricePerItem} to {@code price}, then oldest
     * {@code placedAt}.
     */
    public static Order selectClaimTarget(@NotNull List<Order> candidates, @NotNull PriceInfo price, int volume) {
        return candidates.stream()
                .min(Comparator
                        .comparingInt((Order order) -> Math.abs(order.unclaimedFilled() - volume))
                        .thenComparingDouble(order -> Math.abs(order.pricePerItem() - price.pricePerItem()))
                        .thenComparingLong(Order::placedAt))
                .orElseThrow();
    }

    // ── Cancel ──────────────────────────────────────────────────────────────────

    /**
     * What cancelling {@code order} refunds, in the unit its cancel message reports: coins for a
     * buy order (unfilled units at their price), items for a sell offer (the unfilled remainder).
     */
    private static double expectedRefund(Order order) {
        return order.isBuyOrder() ? order.unfilledAmount() * order.pricePerItem() : order.unfilledAmount();
    }

    /**
     * How far {@link #expectedRefund} may sit from the refund a message reports, in the same unit.
     * The stored fill count may be k/M-truncated by up to {@link Order#FILL_TRUNCATION_MAX}
     * units, which is that many units' worth of coins for a buy order and that many items for a sell
     * offer. A buy's refunded coin total is also rounded by Hypixel itself, hence
     * {@link PriceInfo#COIN_EPSILON}.
     */
    private static double refundTolerance(Order order) {
        return order.isBuyOrder()
                ? Order.FILL_TRUNCATION_MAX * order.pricePerItem() + PriceInfo.COIN_EPSILON
                : Order.FILL_TRUNCATION_MAX;
    }

    /** Matches an order whose {@link #expectedRefund} is within {@link #refundTolerance} of {@code refunded}. */
    private static Predicate<Order> refunds(double refunded) {
        return order -> Math.abs(expectedRefund(order) - refunded) <= refundTolerance(order);
    }

    /** Every order on {@code side}, narrowed to {@code productId} when the message names one. */
    private static Predicate<Order> cancelScope(@Nullable String productId, @NotNull TransactionType.Side side) {
        if (productId == null) return order -> order.side() == side;

        return Order.forProduct(productId, side);
    }

    /**
     * Returns every order a cancellation could describe — the cancel counterpart of
     * {@link #forFillCandidates} and {@link #forClaimCandidates}, with {@link #selectCancelTarget}
     * breaking any tie. The screen-selection hint wins when its live storage entry is a
     * cancellable order in scope. Otherwise every cancellable order in scope whose
     * {@link #expectedRefund} is within {@link #refundTolerance} of {@code refunded} is a
     * candidate, and none may qualify.
     *
     * @param productId the product the message names, or {@code null} when it names none: a buy
     *                  cancellation reports only coins, so every cancellable buy order is in scope
     * @param refunded  what the message reports refunded, in its own unit: coins for a buy order,
     *                  items for a sell offer
     */
    public static List<Order> forCancelCandidates(@Nullable String productId, @NotNull TransactionType transaction, double refunded, List<Order> storage) {
        var inScope = cancelScope(productId, transaction.getSide());

        var clicked = HandledOrderAPI.getForOptions()
                .flatMap(order -> UserOrdersStorage.findById(storage, order.id()))
                .filter(Order::isCancellable)
                .filter(inScope);

        return clicked.map(List::of).orElseGet(() -> storage.stream()
                .filter(Order::isCancellable)
                .filter(inScope)
                .filter(refunds(refunded))
                .toList());
    }

    /**
     * Picks the candidate whose {@link #expectedRefund} lies closest to {@code refunded} (coins for
     * a buy, items for a sell), oldest {@code placedAt} on a tie — the tiebreak
     * {@link #forCancelCandidates} leaves to it when evidence alone leaves more than one.
     */
    public static Order selectCancelTarget(@NotNull List<Order> candidates, double refunded) {
        return candidates.stream()
                .min(Comparator
                        .comparingDouble((Order order) -> Math.abs(expectedRefund(order) - refunded))
                        .thenComparingLong(Order::placedAt))
                .orElseThrow();
    }

    // ── Flip ────────────────────────────────────────────────────────────────────

    /**
     * Returns the {@link PriceInfo} a flip's resulting sell offer should be recorded at.
     *
     * <p>Prefers whatever {@link TransactionAPI} captured from the flip price sign when its
     * {@code matchedBuy} matches {@code matchedBuy.id()}, falling back to
     * {@link PriceInfo#implied(double, int)} over {@code chatPrice} otherwise.
     */
    public static @NotNull PriceInfo resolveForFlip(@NotNull Order matchedBuy, int volume, double chatPrice) {
        var pendingOpt = TransactionAPI.consumeFlip();
        if (pendingOpt.isPresent()) {
            var parsed = pendingOpt.get();
            if (parsed.matchedBuy().id().equals(matchedBuy.id())) {
                return PriceInfo.exact(parsed.sellPrice());
            }

            Util.logMessage("Discarded mismatched TransactionAPI pending flip — expected=%s, pending=%s"
                    .formatted(matchedBuy.id(), parsed.matchedBuy().id()));
        }

        return PriceInfo.implied(chatPrice, volume);
    }

    /**
     * Matches an order whose unclaimed fill is enough to cover
     * {@code volume}, with {@link Order#FILL_TRUNCATION_MAX} of
     * slack for the same screen rounding as everywhere else in this class.
     */
    private static Predicate<Order> coversUnclaimedFill(int volume) {
        return order -> order.unclaimedFilled() + Order.FILL_TRUNCATION_MAX >= volume;
    }

    /**
     * Locates the buy order a flip claims and replaces. The screen-selection hint wins when its
     * live storage entry is a flippable buy order on {@code productId}. Otherwise, among flippable
     * buy orders there whose unclaimed fill covers {@code flipVolume}, picks the closest match on
     * {@code |unclaimedFilled - flipVolume|}, oldest {@code placedAt} on a tie.
     */
    public static Optional<Order> forFlip(String productId, int flipVolume, List<Order> storage) {
        return HandledOrderAPI.getForOptions()
                .flatMap(order -> UserOrdersStorage.findById(storage, order.id()))
                .filter(Order::isFlippable)
                .filter(Order.forProduct(productId, TransactionType.Side.BUY))
                .or(() -> storage.stream()
                        .filter(Order::isFlippable)
                        .filter(Order.forProduct(productId, TransactionType.Side.BUY))
                        .filter(coversUnclaimedFill(flipVolume))
                        .min(Comparator
                                .comparingInt((Order order) -> Math.abs(order.unclaimedFilled() - flipVolume))
                                .thenComparingLong(Order::placedAt)));
    }
}
