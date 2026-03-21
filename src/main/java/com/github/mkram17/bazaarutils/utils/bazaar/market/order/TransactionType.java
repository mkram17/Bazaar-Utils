package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import lombok.Getter;

/**
 * Describes a bazaar transaction using both its side (buy/sell) and method (instant/order).
 *
 * <p>The resolved {@link PriceType} depends on both values: instant transactions use the same
 * market side, while order transactions target the opposite side of the book.</p>
 */
public class TransactionType {

    /**
     * Player intent side of the transaction.
     */
    public enum Side {
        BUY,
        SELL;

        /**
         * Returns the opposite player intent side.
         */
        public Side opposite() {
            return this == BUY ? SELL : BUY;
        }

        /**
         * Maps this side to the matching instant market bucket.
         */
        public PriceType asPriceType() {
            return this == BUY ? PriceType.INSTABUY : PriceType.INSTASELL;
        }

        @Override
        public String toString() {
            return this == BUY ? "Buy" : "Sell";
        }
    }

    public enum Method {
        INSTANT,
        ORDER;

        @Override
        public String toString() {
            return this == INSTANT ? "Instant" : "Order";
        }
    }

    //TransactionType
    @Getter
    private final PriceType priceType;

    @Getter
    private final Side side;

    @Getter
    private final Method method;

    private TransactionType(Side side, Method method) {
        this.side = side; this.method = method;
        this.priceType = resolvePriceType(side, method);
    }

    public static TransactionType of(Side side, Method method) {
        return new TransactionType(side, method);
    }

    /**
     * Resolves the {@link PriceType} for this side+method pair.
     * Instant transactions use the same side; orders use the opposite side.
     */
    public static PriceType resolvePriceType(Side side, Method method) {
        if(method == Method.INSTANT) return side.asPriceType();
        else return side.asPriceType().opposite();
    }

    public boolean isInstant() {
        return method == Method.INSTANT;
    }

    public boolean isOrder() {
        return method == Method.ORDER;
    }

    public boolean isBuy() {
        return side == Side.BUY;
    }

    public boolean isSell() {
        return side == Side.SELL;
    }

    /**
     * Helper to easily check if this transaction resolves to a specific PriceType.
     */
    public boolean is(PriceType targetPriceType) {
        return this.priceType == targetPriceType;
    }

    /**
     * Checks whether this transaction resolves to the same {@link PriceType} as the target.
     *
     * <p>This compares market bucket equivalence, not exact side/method identity.</p>
     */
    public boolean is(TransactionType targetTransactionType) {
        return this.priceType == targetTransactionType.getPriceType();
    }

    public String getString() {
        return method + " " + side;
    }
}