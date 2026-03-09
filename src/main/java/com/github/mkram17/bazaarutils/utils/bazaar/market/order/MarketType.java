package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

public enum MarketType {
    INSTANT {
        @Override
        public PriceType resolvePriceType(TransactionType intention) {
            return intention == TransactionType.BUY ? PriceType.INSTASELL : PriceType.INSTABUY;
        }

        @Override
        @Deprecated
        public OrderType withIntention(OrderType type) {
            return switch (type) {
                case OrderType.BUY -> OrderType.SELL;
                case OrderType.SELL -> OrderType.BUY;
            };
        }
    },
    ORDER {
        @Override
        public PriceType resolvePriceType(TransactionType intention) {
            return intention == TransactionType.BUY ? PriceType.INSTABUY : PriceType.INSTASELL;
        }

        @Override
        @Deprecated
        public OrderType withIntention(OrderType type) {
            return switch (type) {
                case OrderType.BUY -> OrderType.BUY;
                case OrderType.SELL -> OrderType.SELL;
            };
        }
    };

    public abstract PriceType resolvePriceType(TransactionType intention);
    public abstract OrderType withIntention(OrderType type);
}
