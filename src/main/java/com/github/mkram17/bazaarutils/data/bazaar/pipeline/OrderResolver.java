package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.HandledOrderAPI;
import com.github.mkram17.bazaarutils.data.TransactionAPI;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Matches a Bazaar chat event's approximate description of an order back
 * to one specific tracked {@link Order}.
 *
 * <p>Cancellation, claim, and flip resolution all check the screen-selection hint from
 * {@link HandledOrderAPI} first — the player's own click on a specific slot is exact,
 * whereas every algorithmic fallback below only has an approximate price or volume to
 * go on, off by as much as {@link PageOrderParser#FILL_TRUNCATION_MAX} due to the
 * Orders screen's k/M display rounding. Fill resolution has no screen hint to check at
 * all — a fill arrives from chat, not a click — so {@link #forFillCandidates} returns
 * every same-size candidate for {@link #selectFillTarget} to disambiguate separately.
 */
public final class OrderResolver {
    private OrderResolver() {}

    /**
     * Matches a stored buy order against a cancellation's refunded coin
     * total: {@code unfilledAmount × pricePerItem} should equal
     * {@code coinsRefunded}, within a tolerance wide enough to absorb both
     * the k/M rounding possibly baked into the stored fill count and
     * Hypixel's own per-unit display epsilon.
     */
    private static Predicate<Order> buyCancel(double coinsRefunded) {
        return order -> {
            int unfilled = order.originalAmount() - order.filledAmount();
            double expected = unfilled * order.pricePerItem();
            double tolerance = PageOrderParser.FILL_TRUNCATION_MAX * order.pricePerItem() + OrderInfo.COIN_EPSILON;

            return Math.abs(expected - coinsRefunded) <= tolerance;
        };
    }

    /**
     * Matches a stored sell offer against a cancellation's returned item
     * count, which is exactly the offer's unfilled remainder — within
     * {@link PageOrderParser#FILL_TRUNCATION_MAX} to absorb the same k/M
     * rounding as {@link #buyCancel}.
     */
    private static Predicate<Order> sellCancel(int refundedVolume) {
        return order -> Math.abs(order.unfilledAmount() - refundedVolume) <= PageOrderParser.FILL_TRUNCATION_MAX;
    }

    private static Predicate<Order> filledOrder(int volume) {
        return order -> order.originalAmount() == volume;
    }

    /**
     * Price-similarity predicate for claim matching. Tolerance mirrors
     * {@link OrderInfo#computeTolerance}, so the same band applies here as
     * it would to a freshly constructed {@code OrderInfo} for the same
     * price and volume.
     */
    private static Predicate<Order> priceMatch(double pricePerUnit, int volume) {
        double tolerance = OrderInfo.computeTolerance(pricePerUnit, volume);

        return order -> Util.genericIsSimilarValue(pricePerUnit, order.pricePerItem(), tolerance + order.pricePerItem() * 0.01);
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
     * Returns the price a new placement should be recorded at.
     *
     * <p>Prefers whatever {@link TransactionAPI} captured from the
     * confirmation screen — the exact fractional price Hypixel itself
     * records — over {@code chatPrice}, which is only as precise as the
     * chat message's own rounding for large orders.
     */
    public static double resolveForPlacement(double chatPrice) {
        return TransactionAPI.consume().map(OrderInfo::getPricePerItem).orElse(chatPrice);
    }

    /**
     * Locates the buy order a cancellation refers to: the screen-selection
     * hint if the player just clicked one, otherwise the first active buy
     * order whose price and unfilled amount are consistent with
     * {@code coinsRefunded}.
     */
    public static Optional<Order> forBuyCancel(double coinsRefunded, List<Order> storage) {
        return HandledOrderAPI.getForOptions()
                .filter(Order::isCancellable)
                .filter(Order::isBuyOrder)
                .or(() -> storage.stream()
                        .filter(Order::isCancellable)
                        .filter(Order::isBuyOrder)
                        .filter(buyCancel(coinsRefunded))
                        .findFirst());
    }

    /**
     * Locates the sell offer a cancellation refers to: the screen-selection
     * hint if present, otherwise the first active sell offer on
     * {@code productId} whose unfilled remainder matches
     * {@code refundedVolume}.
     */
    public static Optional<Order> forSellCancel(String productId, int refundedVolume, List<Order> storage) {
        return HandledOrderAPI.getForOptions()
                .filter(Order::isCancellable)
                .filter(Order.forProduct(productId, TransactionType.Side.SELL))
                .or(() -> storage.stream()
                        .filter(Order::isCancellable)
                        .filter(Order.forProduct(productId, TransactionType.Side.SELL))
                        .filter(sellCancel(refundedVolume))
                        .findFirst());
    }

    /**
     * Locates the order a claim advances. The screen-selection hint wins
     * outright when present. Otherwise, every live, claimable order on this
     * product and side within {@link #priceMatch} of {@code pricePerUnit}
     * is a candidate; a single candidate is taken as-is, and multiple ones
     * are narrowed by {@link #selectClaimTarget}.
     */
    public static Optional<Order> forClaim(String productId, TransactionType.Side side, double pricePerUnit, int volume, List<Order> storage) {
        var fromScreen = HandledOrderAPI.getForClaim()
                .filter(order -> !order.isTerminal())
                .filter(Order::isClaimable)
                .filter(Order.forProduct(productId, side));

        if (fromScreen.isPresent()) return fromScreen;

        var candidates = storage.stream()
                .filter(order -> !order.isTerminal())
                .filter(Order::isClaimable)
                .filter(Order.forProduct(productId, side))
                .filter(priceMatch(pricePerUnit, volume))
                .toList();

        if (candidates.isEmpty()) return Optional.empty();
        if (candidates.size() == 1) return Optional.of(candidates.getFirst());

        return selectClaimTarget(candidates, volume);
    }

    /**
     * Among several same-price candidates, picks the first whose unclaimed
     * fill actually covers the volume being claimed — the tiebreak
     * {@link #forClaim} falls back to once price similarity alone leaves
     * more than one order in play.
     */
    private static Optional<Order> selectClaimTarget(List<Order> candidates, int claimedVolume) {
        return candidates.stream()
                .filter(coversUnclaimedFill(claimedVolume))
                .findFirst();
    }

    /**
     * Every active order on this product and side whose original volume
     * matches the fill chat message's own volume — the message carries no
     * price, so size is the only identity it offers. Multiple candidates
     * are common when several orders of the same size are live at once;
     * pass the result to {@link #selectFillTarget} to pick one.
     */
    public static List<Order> forFillCandidates(String productId, TransactionType.Side side, int volume, List<Order> storage) {
        return storage.stream()
                .filter(Order::isActive)
                .filter(Order.forProduct(productId, side))
                .filter(filledOrder(volume))
                .toList();
    }

    /**
     * Resolves fill ambiguity among same-size candidates: prefers whichever
     * one currently sits at a competitive book position — {@code data}'s
     * headmap count of zero — since the market fills the best-positioned
     * order first, then falls back to fill-priority order among the rest.
     * {@code data} may be {@code null} when the product has never been
     * registered, in which case selection falls straight through to
     * fill-priority ordering alone.
     */
    public static Order selectFillTarget(List<Order> candidates, @Nullable ProductData data, TransactionType.Side side) {
        var transaction = TransactionType.of(side, TransactionType.Method.ORDER);

        return candidates.stream()
                .filter(order -> data != null && data.positionOf(transaction, order.pricePerItem()) == 0)
                .min(Order.byFillPriority(side))
                .or(() -> candidates.stream().min(Order.byFillPriority(side)))
                .orElseThrow();
    }

    /**
     * Locates the buy order a flip claims and replaces. The
     * screen-selection hint wins outright when present. Otherwise, among
     * flippable buy orders whose unclaimed fill covers {@code flipVolume},
     * picks the one with the smallest overage above it — the tightest fit,
     * rather than an arbitrarily larger order that happens to also
     * qualify.
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