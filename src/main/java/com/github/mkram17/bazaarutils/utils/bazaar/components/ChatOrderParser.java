package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;

import java.util.Optional;

/**
 * Constructs {@link OrderInfo} instances from raw Bazaar chat message values.
 *
 * <p>Hypixel chat messages encode prices in a variety of ways depending on the
 * event type — total coins, per-unit, post-tax, profit — and the correct
 * {@link TransactionType.Side} is sometimes the opposite of what the message
 * text implies. All of that quirk handling lives here so {@link OrderInfo} stays
 * a pure data/similarity class.
 * </p>
 *
 * <p><b>Side note on {@link #parseFlipped}:</b> the returned {@code OrderInfo}'s
 * {@code pricePerItem} field carries <em>profit-per-unit</em>, not a real market
 * price. This is intentional and documented — callers must not treat it as a
 * price.
 * </p>
 */
public final class ChatOrderParser {

    private static volatile long lastTaxWarningMs = 0L;
    private static final long TAX_WARN_COOLDOWN_MS = 60_000L;

    public static void warnTaxMisconfiguration(String context) {
        long now = System.currentTimeMillis();
        if (now - lastTaxWarningMs < TAX_WARN_COOLDOWN_MS) return;
        lastTaxWarningMs = now;

        Util.notifyError(context + " Run /bu config to fix your Account Upgrade setting.", new Throwable());
    }

    private ChatOrderParser() {}

    /** ORDER_CREATED buy: chat gives total coins; per-unit = total ÷ volume. */
    public static Optional<OrderInfo> parseBuyCreated(String name, double totalCoins, int volume) {
        return OrderInfo.of(name, TransactionType.Side.BUY, Util.truncateNum(totalCoins / volume), volume);
    }

    /**
     * ORDER_CREATED sell: chat reports post-tax total. We reverse the tax to
     * recover the pre-tax per-unit price that appears in the order book and tooltips.
     */
    public static Optional<OrderInfo> parseSellCreated(String name, double totalCoins, int volume) {
        double tax = BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE.userBazaarTax;
        double postTaxPerUnit = Util.truncateNum(totalCoins / volume);
        double rawPreTax = postTaxPerUnit / ((100.0 - tax) / 100.0);
        double preTaxPerUnit = Util.truncateNum(rawPreTax);
        return OrderInfo.of(name, TransactionType.Side.SELL, preTaxPerUnit, volume);
    }

    /** ORDER_CLAIMED buy: chat gives per-unit price directly. */
    public static Optional<OrderInfo> parseClaimedBuy(String name, double pricePerUnit, int volume) {
        return OrderInfo.of(name, TransactionType.Side.BUY,
                Util.truncateNum(pricePerUnit), volume);
    }

    /**
     * ORDER_CLAIMED sell: chat gives total post-tax payout. We store post-tax
     * per-unit so {@link OrderMatcher#sellClaim} can compare against stored
     * pre-tax prices.
     */
    public static Optional<OrderInfo> parseClaimedSell(String name, double totalCoins, int volume) {
        return OrderInfo.of(name, TransactionType.Side.SELL,
                Util.truncateNum(totalCoins / volume), volume);
    }

    /** ORDER_FILLED / ORDER_CANCELLED: price is irrelevant for matching. */
    public static Optional<OrderInfo> parseFilled(String name, TransactionType.Side side, int volume) {
        return OrderInfo.of(name, side, 0.0, volume);
    }

    /**
     * INSTA_BUY: the player bought from sell offers, so the consumed side is SELL.
     * Chat gives total coins paid.
     */
    public static Optional<OrderInfo> parseInstantBuy(String name, double totalCoins, int volume) {
        return OrderInfo.of(name, TransactionType.Side.SELL,
                Util.truncateNum(totalCoins / volume), volume);
    }

    /**
     * INSTA_SELL: the player sold to buy orders, so the consumed side is BUY.
     * Chat gives total coins received.
     */
    public static Optional<OrderInfo> parseInstantSell(String name, double totalCoins, int volume) {
        return OrderInfo.of(name, TransactionType.Side.BUY,
                Util.truncateNum(totalCoins / volume), volume);
    }

    /**
     * ORDER_FLIPPED: chat gives total expected profit (pre-tax), not the sell price.
     *
     * <p><b>WARNING:</b> the returned {@code OrderInfo}'s {@code pricePerItem}
     * carries <em>raw profit-per-unit</em> (totalProfit / volume) with no truncation applied.
     * It is not a market price. The actual sell price must be recovered at the call site:
     * <pre>
     *   double sellPrice = Util.truncateNum(buyPricePerItem + info.getPricePerItem());
     * </pre>
     * The profit reported by Hypixel is already the listed sell price minus the listed buy
     * price — tax is baked into the flip profit figure and must NOT be divided out again.
     * Truncation is intentionally deferred to the call site to avoid precision loss
     * in the intermediate profit-per-unit value.
     */
    public static Optional<OrderInfo> parseFlipped(String name, double totalProfit, int volume) {
        return OrderInfo.of(name, TransactionType.Side.SELL,
                totalProfit / volume, volume);  // raw; caller applies tax and truncates
    }
}