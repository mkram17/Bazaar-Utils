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
 * Resolution strategies for matching a Bazaar chat event to a specific tracked order.
 *
 * <p>Every public method prefers the screen-selection hint from
 * {@link com.github.mkram17.bazaarutils.data.HandledOrderAPI} over algorithmic matching. The
 * hint is exact — the player clicked that specific slot — whereas algorithmic fallbacks rely on
 * price/volume approximation that may be off by up to {@code FILL_TRUNCATION_MAX} due to k/M
 * display truncation.
 *
 * <p>Fill-event resolution is a special case: the fill chat message carries only product name
 * and volume, no price. {@link #forFillCandidates} therefore matches on {@code originalAmount}
 * alone and returns multiple candidates; {@link #selectFillTarget} resolves ties by competitive
 * position then FIFO.
 */
public final class OrderResolver {
    private OrderResolver() {}

    private static Predicate<Order> buyCancel(double coinsRefunded) {
        return order -> {
            int unfilled = order.originalAmount() - order.filledAmount();
            double expected = unfilled * order.pricePerItem();
            double tolerance = PageOrderParser.FILL_TRUNCATION_MAX * order.pricePerItem() + OrderInfo.COIN_EPSILON;

            return Math.abs(expected - coinsRefunded) <= tolerance;
        };
    }

    private static Predicate<Order> sellCancel(int refundedVolume) {
        return order -> Math.abs(order.unfilledAmount() - refundedVolume) <= PageOrderParser.FILL_TRUNCATION_MAX;
    }

    private static Predicate<Order> filledOrder(int volume) {
        return order -> order.originalAmount() == volume;
    }

    /**
     * Price-similarity predicate for claim matching.
     * Tolerance mirrors {@link OrderInfo#computeTolerance} so the threshold
     * is consistent regardless of whether an OrderInfo is constructed.
     */
    private static Predicate<Order> priceMatch(double pricePerUnit, int volume) {
        double tolerance = OrderInfo.computeTolerance(pricePerUnit, volume);

        return order -> Util.genericIsSimilarValue(pricePerUnit, order.pricePerItem(), tolerance + order.pricePerItem() * 0.01);
    }

    private static Predicate<Order> coversUnclaimedFill(int volume) {
        return order -> order.unclaimedFilled() + PageOrderParser.FILL_TRUNCATION_MAX >= volume;
    }

    /**
     * Returns the authoritative per-unit price for a new order placement.
     *
     * <p>Prefers the confirmation screen ({@link TransactionAPI}), which carries the
     * exact fractional price Hypixel records. Falls back to {@code chatPrice},
     * which may be rounded for large orders.
     */
    public static double resolveForPlacement(double chatPrice) {
        return TransactionAPI.consume().map(OrderInfo::getPricePerItem).orElse(chatPrice);
    }

    /**
     * Locates the buy order to cancel. Prefers the screen-selection hint; falls back to
     * matching {@code pricePerItem × unfilledAmount ≈ coinsRefunded} within tolerance.
     */
    public static Optional<Order> forBuyCancel(double coinsRefunded, List<Order> storage) {
        return HandledOrderAPI.getForOptions()
                .filter(Order::isActive)
                .filter(Order::isBuyOrder)
                .or(() -> storage.stream()
                        .filter(Order::isActive)
                        .filter(Order::isBuyOrder)
                        .filter(buyCancel(coinsRefunded))
                        .findFirst());
    }

    /**
     * Locates the sell offer to cancel. Prefers the screen-selection hint; falls back to
     * matching unfilled remainder against the returned item count within tolerance.
     */
    public static Optional<Order> forSellCancel(String productId, int refundedVolume, List<Order> storage) {
        return HandledOrderAPI.getForOptions()
                .filter(Order::isActive)
                .filter(Order.forProduct(productId, TransactionType.Side.SELL))
                .or(() -> storage.stream()
                        .filter(Order::isActive)
                        .filter(Order.forProduct(productId, TransactionType.Side.SELL))
                        .filter(sellCancel(refundedVolume))
                        .findFirst());
    }

    /**
     * Locates the order to advance a claim on. Prefers the screen-selection hint; falls
     * back to price-similarity matching against live claimable orders. When multiple
     * candidates match on price, selects the one whose unclaimed fill covers the volume.
     */
    public static Optional<Order> forClaim(String productId, TransactionType.Side side, double pricePerUnit, int volume, List<Order> storage) {
        var fromScreen = HandledOrderAPI.getForClaim()
                .filter(Order::isLive)
                .filter(Order::isClaimable)
                .filter(Order.forProduct(productId, side));

        if (fromScreen.isPresent()) return fromScreen;

        var candidates = storage.stream()
                .filter(Order::isLive)
                .filter(Order::isClaimable)
                .filter(Order.forProduct(productId, side))
                .filter(priceMatch(pricePerUnit, volume))
                .toList();

        if (candidates.isEmpty()) return Optional.empty();
        if (candidates.size() == 1) return Optional.of(candidates.getFirst());

        return selectClaimTarget(candidates, volume);
    }

    private static Optional<Order> selectClaimTarget(List<Order> candidates, int claimedVolume) {
        return candidates.stream()
                .filter(coversUnclaimedFill(claimedVolume))
                .findFirst();
    }

    /**
     * All active orders for this product and side whose original volume matches the
     * filled count. Returns multiple candidates when several orders share the same size;
     * pass the result to {@link #selectFillTarget} to resolve.
     */
    public static List<Order> forFillCandidates(String productId, TransactionType.Side side, int volume, List<Order> storage) {
        return storage.stream()
                .filter(Order::isActive)
                .filter(Order.forProduct(productId, side))
                .filter(filledOrder(volume))
                .toList();
    }

    /**
     * Picks the fill target from candidates. Prefers the order at competitive position
     * (positionOf == 0), then the earliest-queued. {@code data} may be null — falls
     * through to queue-position selection.
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
     * Locates the buy order to claim for a flip. Prefers the screen-selection hint; falls
     * back to the flippable buy with the smallest unclaimed overage above the flip volume.
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