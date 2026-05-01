package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;

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
}