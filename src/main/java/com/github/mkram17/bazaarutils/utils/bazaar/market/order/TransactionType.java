package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import lombok.Getter;

public class TransactionType {

    public enum Side {
        BUY,
        SELL;

        public Side opposite() {
            return this == BUY ? SELL : BUY;
        }

        public PriceType asPriceType() {
            return this == BUY ? PriceType.INSTABUY : PriceType.INSTASELL;
        }

        public String getString() {
            return this == BUY ? "Buy" : "Sell";
        }
    }

    public enum Method {
        INSTANT,
        ORDER;

        public String getString() {
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof TransactionType other) return priceType == other.priceType;
        if (obj instanceof PriceType otherPriceType) return priceType == otherPriceType;
        return false;
    }

    @Override
    public int hashCode() {
        return priceType != null ? priceType.hashCode() : 0;
    }

    public String getString() {
        return method.getString() + " " + side.getString();
    }
}