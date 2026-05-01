package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.HandledOrderAPI;
import com.github.mkram17.bazaarutils.data.TransactionAPI;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceQuantity;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Matches a Bazaar chat event's ({@link BazaarChatEvent}) approximate description of an order back
 * to one specific tracked {@link Order}, and picks the best available price to record for a newly
 * placed or flipped one.
 *
 * <p>Fill, claim, and cancel resolution share one shape: {@code for*Candidates} narrows storage to
 * every order the event could describe, {@code select*Target} breaks any tie that remains.
 *
 * <p>Cancellation, claim, and flip resolution all check the screen-selection hint from
 * {@link HandledOrderAPI} first — the player's own click on a specific slot is exact — and only
 * then fall back to matching on what the chat event reports. Those fallbacks absorb two kinds of
 * imprecision: unit counts the Orders screen shows k/M-truncated, tolerated up to
 * {@link PageOrderParser#FILL_TRUNCATION_MAX}, and prices derived from rounded displayed totals,
 * tolerated through {@link PriceQuantity}. Fill resolution checks no hint at all:
 * {@link #forFillCandidates} returns every same-size candidate for {@link #selectFillTarget} to
 * disambiguate.
 */
public final class OrderResolver {
    private OrderResolver() {}

    // ── Placement ───────────────────────────────────────────────────────────────

    /**
     * Returns the price a new placement should be recorded at, as a {@link PriceQuantity} over
     * {@code volume}: the price {@link TransactionAPI} captured, marked exact, or
     * {@code chatPrice} marked inexact when none was captured, since it was derived from a
     * displayed total.
     *
     * <p>Consumes {@link TransactionAPI}'s pending confirmation, if there is one.
     */
    public static PriceQuantity resolveForPlacement(int volume, double chatPrice) {
        return TransactionAPI.consume()
                .map(info -> PriceQuantity.of(info.getPricePerItem(), volume, true))
                .orElseGet(() -> PriceQuantity.of(chatPrice, volume, false));
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
     * Locates the order a claim advances. The screen-selection hint wins when it is a claimable
     * order on this product and side. Otherwise every claimable order there whose price matches
     * {@code reference} per {@link Order#forPrice(PriceQuantity)} is a candidate: a single one is
     * returned as-is, without checking its unclaimed fill; several are narrowed by
     * {@link #selectClaimTarget} against {@code reference}'s volume, and none may qualify.
     */
    public static List<Order> forClaimCandidates(@NotNull String productId, @NotNull TransactionType transaction, @NotNull PriceQuantity reference, List<Order> storage) {
        var clicked = HandledOrderAPI.getForClaim()
                .flatMap(order -> UserOrdersStorage.findById(storage, order.id()))
                .filter(Order::isActionable)
                .filter(Order.forProduct(productId, transaction.getSide()));

        return clicked.map(List::of).orElseGet(() -> storage.stream()
                .filter(Order::isActionable)
                .filter(Order.forProduct(productId, transaction.getSide()))
                .filter(Order.forPrice(reference))
                .filter(order -> order.originalAmount() - order.claimedAmount() + PageOrderParser.FILL_TRUNCATION_MAX >= reference.volume())
                .toList());
    }

    /**
     * Picks the first candidate, in list order, whose unclaimed fill covers {@code volume} — the
     * tiebreak {@link #forClaimCandidates} falls back to when price matching alone leaves more than one.
     */
    public static Order selectClaimTarget(@NotNull List<Order> candidates, @NotNull PriceQuantity reference) {
        return candidates.stream()
                .min(Comparator
                        .comparingInt((Order order) -> Math.abs(order.unclaimedFilled() - reference.volume()))
                        .thenComparingDouble(order -> Math.abs(order.pricePerItem() - reference.pricePerItem()))
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
     * Matches a stored buy order against a cancellation's refunded coin total: {@link #expectedRefund}
     * must land within {@link PageOrderParser#FILL_TRUNCATION_MAX} units' worth of coins, since the
     * stored fill count may be k/M-truncated, plus {@link Order#COIN_EPSILON}, Hypixel's
     * rounding of the refunded total itself.
     */
    private static Predicate<Order> refundsCoins(double coinsRefunded) {
        return order -> Math.abs(expectedRefund(order) - coinsRefunded)
                <= PageOrderParser.FILL_TRUNCATION_MAX * order.pricePerItem() + Order.COIN_EPSILON;
    }

    /**
     * Matches a stored sell offer against a cancellation's returned item count: {@link #expectedRefund}
     * must be within {@link PageOrderParser#FILL_TRUNCATION_MAX} of {@code refundedVolume},
     * absorbing a k/M-truncated stored fill count.
     */
    private static Predicate<Order> refundsItems(int refundedVolume) {
        return order -> Math.abs(expectedRefund(order) - refundedVolume) <= PageOrderParser.FILL_TRUNCATION_MAX;
    }

    /**
     * Shared shape of both cancel lookups. The screen-selection hint wins when it is a cancellable
     * order within {@code inScope}. Otherwise every cancellable order there that {@code refundMatches}
     * accepts is a candidate, and none may qualify.
     */
    private static List<Order> cancelCandidates(List<Order> storage, Predicate<Order> inScope, Predicate<Order> refundMatches) {
        var clicked = HandledOrderAPI.getForOptions()
                .filter(Order::isCancellable)
                .filter(inScope);

        return clicked.map(List::of).orElseGet(() -> storage.stream()
                .filter(Order::isCancellable)
                .filter(inScope)
                .filter(refundMatches)
                .toList());
    }

    /**
     * Locates the buy order a cancellation refers to. A buy cancellation names no product, so
     * every cancellable buy order is in scope; see {@link #refundsCoins}.
     */
    public static List<Order> forBuyCancelCandidates(double coinsRefunded, List<Order> storage) {
        return cancelCandidates(storage, Order::isBuyOrder, refundsCoins(coinsRefunded));
    }

    /**
     * Locates the sell offer a cancellation refers to, within {@code productId}; see {@link #refundsItems}.
     */
    public static List<Order> forSellCancelCandidates(@NotNull String productId, int refundedVolume, List<Order> storage) {
        return cancelCandidates(storage, Order.forProduct(productId, TransactionType.Side.SELL), refundsItems(refundedVolume));
    }

    /**
     * Picks the candidate whose {@link #expectedRefund} lies closest to {@code refunded} (coins for
     * a buy, items for a sell), oldest {@code placedAt} on a tie — the tiebreak the
     * {@code for*CancelCandidates} lookups fall back to when evidence alone leaves more than one.
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
     * Returns the price a flip's resulting sell offer should be recorded at.
     *
     * <p>Prefers whatever {@link TransactionAPI} captured from the flip price
     * sign — the exact price the player sent — over {@code chatPrice}, which
     * is only as precise as {@code totalProfit}'s own rounding for large flips.
     */
    public static PriceQuantity resolveForFlip(int volume, double chatPrice) {
        return TransactionAPI.consumeFlip()
                .map(parsed -> PriceQuantity.of(parsed.sellPrice(), volume, true))
                .orElseGet(() -> PriceQuantity.of(chatPrice, volume, false));
    }

    /**
     * Matches an order whose unclaimed fill is enough to cover
     * {@code volume}, with {@link PageOrderParser#FILL_TRUNCATION_MAX} of
     * slack for the same screen rounding as everywhere else in this class.
     */
    private static Predicate<Order> coversUnclaimedFill(int volume) {
        return order -> order.unclaimedFilled() + PageOrderParser.FILL_TRUNCATION_MAX >= volume;
    }

    /**
     * Locates the buy order a flip claims and replaces. The screen-selection hint wins when it is
     * a flippable buy order on {@code productId}. Otherwise, among flippable buy orders there
     * whose unclaimed fill covers {@code flipVolume}, picks the one with the least unclaimed fill.
     * That is the tightest fit when every candidate fully covers the flip, but
     * {@link #coversUnclaimedFill} tolerates a slightly short one, which then wins over a fully
     * covering order.
     */
    public static Optional<Order> forFlip(String productId, int flipVolume, List<Order> storage) {
        return HandledOrderAPI.getForOptions()
                .filter(Order::isFlippable)
                .filter(Order.forProduct(productId, TransactionType.Side.BUY))
                .or(() -> storage.stream()
                        .filter(Order::isFlippable)
                        .filter(Order.forProduct(productId, TransactionType.Side.BUY))
                        .filter(coversUnclaimedFill(flipVolume))
                        .min(Comparator.comparingInt(order -> order.unclaimedFilled() - flipVolume)));
    }
}