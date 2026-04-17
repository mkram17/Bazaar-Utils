package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.components.ChatOrderParser;

/**
 * Pure static predicates for matching a stored {@link Order} against a parsed
 * chat event value.
 */
public final class OrderMatcher {
    /**
     * CLAIM / FLIP target selection — the stored order has enough filled-but-not-yet-
     * claimed volume to satisfy the volume currently being claimed or flipped.
     *
     * A tolerance of 100 units absorbs k/M display truncation in the screen-derived
     * claimedAmount, which can make unclaimedFilled appear up to ~99 units too low.
     */
    private static final int SCREEN_TRUNCATION_TOLERANCE = 100;

    private OrderMatcher() {}

    /**
     * BUY CANCEL — refund = pricePerItem × unfilledAmount.
     * Screen fill is k/M-truncated (floored to nearest 100 units), so our stored
     * filledAmount may be up to 99 units too low, and our unfilled count up to 99
     * units too high. Tolerance absorbs that maximum over-count.
     */
    public static boolean buyCancel(Order order, double coinsRefunded) {
        int unfilled = order.originalAmount() - order.filledAmount();
        double expected = unfilled * order.pricePerItem();
        double tolerance = SCREEN_TRUNCATION_TOLERANCE * order.pricePerItem() + OrderInfo.COIN_EPSILON;
        return Math.abs(expected - coinsRefunded) <= tolerance;
    }

    /**
     * SELL CANCEL — chat reports the count of items returned, which equals the
     * unfilled remainder of the offer.
     */
    public static boolean sellCancel(Order order, int refundedVolume) {
        return order.unfilledAmount() == refundedVolume;
    }

    /**
     * ORDER FILLED — chat reports the original order volume on whole-order completion.
     * Partial fills do not produce this message.
     */
    public static boolean filledOrder(Order order, int volume) {
        return order.originalAmount() == volume;
    }

    /**
     * SELL CLAIM — {@code claimInfo} carries post-tax per-unit (as set by
     * {@link ChatOrderParser#parseClaimedSell}). The stored order carries pre-tax
     * per-unit. Tax is reversed here to produce the expected post-tax value and
     * compared within the order's tolerance band.
     */
    public static boolean sellClaim(Order order, OrderInfo claimInfo) {
        double tax = BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE.userBazaarTax;
        double expectedPostTax = Util.truncateNum(order.pricePerItem() * ((100.0 - tax) / 100.0));
        return claimInfo.isPriceSimilarTo(expectedPostTax);
    }

    /**
     * CLAIM / FLIP target selection — the stored order has enough filled-but-not-yet-
     * claimed volume to satisfy the volume currently being claimed or flipped.
     */
    public static boolean coversUnclaimedFill(Order order, int volume) {
        return order.unclaimedFilled() + SCREEN_TRUNCATION_TOLERANCE >= volume;
    }
}