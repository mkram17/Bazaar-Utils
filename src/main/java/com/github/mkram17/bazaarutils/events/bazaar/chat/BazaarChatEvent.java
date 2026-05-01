package com.github.mkram17.bazaarutils.events.bazaar.chat;

import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Raw Bazaar chat event, carrying exactly the values the regex captured.
 *
 * <p>No product ID resolution, tax arithmetic, price derivation, or order matching is
 * applied at this layer. Each subtype documents which fields are safe for which operations
 * (e.g. which price is pre-tax vs post-tax) so that consuming data sources do not have to
 * re-derive those properties.
 */
public abstract sealed class BazaarChatEvent extends SkyBlockEvent
        permits BazaarChatEvent.BuyOrderCreated,
        BazaarChatEvent.SellOfferCreated,
        BazaarChatEvent.BuyOrderCancelled,
        BazaarChatEvent.SellOfferCancelled,
        BazaarChatEvent.BuyOrderFilled,
        BazaarChatEvent.SellOfferFilled,
        BazaarChatEvent.BuyOrderClaimed,
        BazaarChatEvent.SellOfferClaimed,
        BazaarChatEvent.BuyOrderFlipped,
        BazaarChatEvent.InstantBuy,
        BazaarChatEvent.InstantSell {
    /**
     * Wall-clock epoch milliseconds at which this message was received by the chat handler.
     * Used as the {@link com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin} timestamp
     * for the book mutation and storage write produced by the consuming data source.
     */
    @Getter
    public final long receivedAt;

    protected BazaarChatEvent(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    /**
     * "Buy Order Setup! {amount}x {product} for {totalCoins} coins"
     *
     * <p>{@code totalCoins} is the total coins committed, with no tax. Hypixel rounds the
     * displayed total for orders whose value exceeds
     * {@link com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo#FOLDING_THRESHOLD},
     * so the per-unit price derived as {@code totalCoins / amount} may carry up to
     * {@code COIN_EPSILON / amount} error. Prefer the confirmation screen price via
     * {@link com.github.mkram17.bazaarutils.data.TransactionAPI} when available.
     */
    public static final class BuyOrderCreated extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        /** Total coins committed. No tax. Subject to rounding for large orders. */
        public final double totalCoins;

        public final int amount;

        public BuyOrderCreated(long receivedAt, String product, double totalCoins, int amount) {
            super(receivedAt);
            this.product = product;
            this.totalCoins = totalCoins;
            this.amount = amount;
        }
    }

    /**
     * "Sell Offer Setup! {amount}x {product} for {totalCoins} coins"
     *
     * <p>{@code totalCoins} is the post-tax payout Hypixel will deliver when the offer fills.
     * The pre-tax listed price (what appears in the order book and on tooltips) is:
     * <pre>
     *   preTaxPerUnit = truncate(truncate(totalCoins / amount) / ((100 − taxPct) / 100))
     * </pre>
     * The double truncation matches Hypixel's internal rounding. Subject to the same large-order
     * rounding as {@link BuyOrderCreated}; prefer the confirmation screen price when available.
     */
    public static final class SellOfferCreated extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        /** Post-tax total as reported by Hypixel. */
        public final double totalCoins;

        public final int amount;

        public SellOfferCreated(long receivedAt, String product, double totalCoins, int amount) {
            super(receivedAt);
            this.product = product;
            this.totalCoins = totalCoins;
            this.amount = amount;
        }
    }

    /**
     * "Cancelled! Refunded {refundedCoins} coins from cancelling Buy Order"
     *
     * <p>Item name and volume are absent. The stored order is matched by
     * {@code pricePerItem × unfilledAmount ≈ refundedCoins}.
     */
    public static final class BuyOrderCancelled extends BazaarChatEvent {
        /** Coins returned. Approximately {@code pricePerItem × unfilledAmount} of the cancelled order. */
        public final double refundedCoins;

        public BuyOrderCancelled(long receivedAt, double refundedCoins) {
            super(receivedAt);
            this.refundedCoins = refundedCoins;
        }
    }

    /**
     * "Cancelled! Refunded {amount}x {product} from cancelling Sell Offer"
     *
     * <p>{@code amount} equals the unfilled remainder of the cancelled offer. No coin
     * value is present.
     */
    public static final class SellOfferCancelled extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        /** Items returned; equals the unfilled remainder of the cancelled sell offer. */
        public final int amount;

        public SellOfferCancelled(long receivedAt, String product, int amount) {
            super(receivedAt);
            this.product = product;
            this.amount = amount;
        }
    }

    /**
     * "Your Buy Order for {amount}x {product} was filled"
     *
     * <p>Price is absent. Consumers match stored orders by product and original volume.
     */
    public static final class BuyOrderFilled extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        /** Original order volume. Price is not present in this message. */
        public final int amount;

        public BuyOrderFilled(long receivedAt, String product, int amount) {
            super(receivedAt);
            this.product = product;
            this.amount = amount;
        }
    }

    /**
     * "Your Sell Offer for {amount}x {product} was filled"
     *
     * <p>Price is absent. Consumers match stored orders by product and original volume.
     */
    public static final class SellOfferFilled extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        /** Original order volume. Price is not present in this message. */
        public final int amount;

        public SellOfferFilled(long receivedAt, String product, int amount) {
            super(receivedAt);
            this.product = product;
            this.amount = amount;
        }
    }

    /**
     * "Claimed {amount}x {product} worth {totalCoins} coins bought for {pricePerUnit} each"
     *
     * <p>No tax on buy claims. {@code pricePerUnit} is Hypixel's recorded per-unit price
     * and is the authoritative field for matching stored orders.
     */
    public static final class BuyOrderClaimed extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        public final int amount;

        /** Total value of claimed items (amount × pricePerUnit). No tax. */
        public final double totalCoins;

        /** Per-unit price as recorded by Hypixel. Authoritative for order matching. */
        public final double pricePerUnit;

        public BuyOrderClaimed(long receivedAt, String product, int amount, double totalCoins, double pricePerUnit) {
            super(receivedAt);
            this.product = product;
            this.amount = amount;
            this.totalCoins = totalCoins;
            this.pricePerUnit = pricePerUnit;
        }
    }

    /**
     * "Claimed {totalCoins} coins from selling {amount}x {product} at {pricePerUnit} each"
     *
     * <p>{@code totalCoins} is the post-tax payout. {@code pricePerUnit} is the pre-tax
     * listed price and can be compared directly against stored orders without tax reversal.
     */
    public static final class SellOfferClaimed extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        public final int amount;

        /** Post-tax payout. Equals {@code amount × pricePerUnit × (1 − taxRate)}. */
        public final double totalCoins;

        /** Pre-tax listed price per unit. Use this field for order matching. */
        public final double pricePerUnit;

        public SellOfferClaimed(long receivedAt, String product, int amount, double totalCoins, double pricePerUnit) {
            super(receivedAt);
            this.product = product;
            this.amount = amount;
            this.totalCoins = totalCoins;
            this.pricePerUnit = pricePerUnit;
        }
    }

    /**
     * "Order Flipped! {amount}x {product} for {totalProfit} coins of total expected profit"
     *
     * <p>{@code totalProfit} is not a price — it is {@code (sellPrice − buyPrice) × amount},
     * already net of Bazaar tax. Do not apply a tax factor when recovering the sell price:
     * <pre>
     *   sellPrice = truncate(matchedBuy.pricePerItem() + totalProfit / amount)
     * </pre>
     */
    public static final class BuyOrderFlipped extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        public final int amount;

        /**
         * Total expected profit as reported by Hypixel. Not a price.
         * Divide by {@code amount} to get per-unit profit; add to the matched buy price to
         * recover the sell price. Do not apply tax reversal.
         */
        public final double totalProfit;

        public BuyOrderFlipped(long receivedAt, String product, int amount, double totalProfit) {
            super(receivedAt);
            this.product = product;
            this.amount = amount;
            this.totalProfit = totalProfit;
        }
    }

    /**
     * "Bought {amount}x {product} for {totalCoins} coins"
     *
     * <p>Consumed sell offers. {@code totalCoins} is the total paid; no tax on
     * instant-buy. Per-unit = {@code totalCoins / amount}.
     */
    public static final class InstantBuy extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        /** Total paid across matched sell levels. No tax. */
        public final double totalCoins;

        public final int amount;

        public InstantBuy(long receivedAt, String product, double totalCoins, int amount) {
            super(receivedAt);
            this.product = product;
            this.totalCoins = totalCoins;
            this.amount = amount;
        }
    }

    /**
     * "Sold {amount}x {product} for {totalCoins} coins"
     *
     * <p>Consumed buy orders at the best bid prices. {@code totalCoins} is the post-tax
     * payout; the Bazaar sell tax has already been deducted by Hypixel before display.
     * This source uses the coin total to walk volume off the INSTASELL (bids) book —
     * no per-unit price is derived from it.
     */
    public static final class InstantSell extends BazaarChatEvent {
        /** Display name as it appears in chat; not yet resolved to a product ID. */
        public final String product;

        /** Post-tax total received. Bazaar sell tax has already been deducted. */
        public final double totalCoins;

        public final int amount;

        public InstantSell(long receivedAt, String product, double totalCoins, int amount) {
            super(receivedAt);
            this.product = product;
            this.totalCoins = totalCoins;
            this.amount = amount;
        }
    }
}