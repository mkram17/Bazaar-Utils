package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataQuery;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;

import java.util.List;
import java.util.Optional;

/**
 * Competitive standing of an order relative to the current top of book on its side.
 *
 * <ul>
 *   <li>{@link #COMPETITIVE} — no orders ahead; this price is the best available.</li>
 *   <li>{@link #MATCHED} — tied with the best price, but at least one external order
 *       also occupies the level (excludes the player's own when self-outbid is off).</li>
 *   <li>{@link #OUTBID} — at least one order is strictly ahead in the queue.</li>
 * </ul>
 */
public enum PricingPosition {
    COMPETITIVE,
    MATCHED,
    OUTBID;

    /**
     * Returns the price that places an order at this standing relative to {@code market},
     * clamped to Hypixel's placement bounds.
     *
     * <p>COMPETITIVE steps 0.1 coins in the better direction (above market for bids, below for asks).
     * MATCHED returns {@code market} unchanged. OUTBID steps 0.1 coins in the worse direction.
     * The result is then clamped between {@link PriceInfo#MINIMUM_PRICE} and Hypixel's
     * bid floor / ask ceiling.
     *
     * @param market      the current top-of-book market price
     * @param transaction the transaction type and method context
     * @return the adjusted price
     */
    public double adjust(double market, TransactionType transaction) {
        boolean higherIsBetter = transaction.higherIsBetter();

        double raw = switch (this) {
            case COMPETITIVE -> higherIsBetter ? market + 0.1 : market - 0.1;
            case MATCHED -> market;
            case OUTBID -> higherIsBetter ? market - 0.1 : market + 0.1;
        };

        if (higherIsBetter) {
            // Raise to the stricter of the absolute floor and Hypixel's 2/3 bid floor.
            return Math.max(Math.max(PriceInfo.MINIMUM_PRICE, PriceInfo.minimumBid(market)), raw);
        } else {
            // Clamp between the absolute floor and Hypixel's 3/2 ask ceiling.
            return Math.clamp(raw, PriceInfo.MINIMUM_PRICE, PriceInfo.maximumAsk(market));
        }
    }

    public static PricingPosition of(int ahead, int totalAtPrice, int ownAtPrice, boolean selfOutbid) {
        if (ahead > 0) return OUTBID;
        int external = selfOutbid ? Math.max(0, totalAtPrice - 1) : Math.max(0, totalAtPrice - ownAtPrice);

        return external == 0 ? COMPETITIVE : MATCHED;
    }


    /**
     * The raw market facts behind an order's standing, before any self-outbid policy is applied.
     *
     * @param ahead        number of orders strictly ahead in the queue (a better price). If > 0,
     *                     the order is outbid outright — self-outbid plays no part in that case.
     * @param totalAtPrice total order count at this exact price level, everyone included.
     * @param ownAtPrice   how many of those are the player's own active orders.
     */
    public record PositionContext(int ahead, int totalAtPrice, int ownAtPrice) {
        /**
         * Classifies this standing into a {@link PricingPosition}.
         *
         * @param selfOutbid when {@code true}, the player's own orders at this price count as
         *                   external competition; when {@code false}, they're excluded, so a price
         *                   level occupied solely by the player's own volume reports COMPETITIVE
         *
         * @return the resulting competitive position
         */
        public PricingPosition classify(boolean selfOutbid) {
            return of(ahead, totalAtPrice, ownAtPrice, selfOutbid);
        }
    }
}