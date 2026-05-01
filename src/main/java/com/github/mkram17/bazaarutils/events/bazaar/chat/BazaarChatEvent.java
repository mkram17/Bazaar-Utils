package com.github.mkram17.bazaarutils.events.bazaar.chat;

import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Raw chat signals for Bazaar interactions.
 *
 * <p>Each subtype carries exactly the primitive values the regex captured — display
 * name strings, coin totals, and item counts. No product ID resolution, tax
 * arithmetic, or price derivation has been applied.
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

    @Getter
    public final long receivedAt;

    protected BazaarChatEvent(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    /**
     * "Buy Order Setup! {amount}x {product} for {totalCoins} coins"
     *
     * <p>{@code totalCoins} is the total committed. No tax. For large orders Hypixel
     * rounds the displayed total, so {@code totalCoins / amount} may carry up to
     * {@code COIN_EPSILON / amount} per-unit error.
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
     * <p>{@code totalCoins} is the post-tax payout. Consumers recover the pre-tax
     * listed price as:
     * <pre>
     *   preTaxPerUnit = truncate((totalCoins / amount) / ((100 − taxPct) / 100))
     * </pre>
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
     * <p>{@code totalProfit} is not a price — it equals {@code (sellPrice − buyPrice) × amount}.
     * Tax is already folded in; do not reverse it. The sell price is recovered as:
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
     * <p>Consumed buy orders. {@code totalCoins} is post-tax; Bazaar sell tax is
     * deducted. Per-unit = {@code totalCoins / amount} (post-tax).
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