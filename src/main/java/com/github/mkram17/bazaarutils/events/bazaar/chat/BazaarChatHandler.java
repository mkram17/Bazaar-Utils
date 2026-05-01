package com.github.mkram17.bazaarutils.events.bazaar.chat;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.RegexSwitch;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Matches Bazaar chat messages and fires the corresponding {@link BazaarChatEvent}.
 *
 * <p>Owns regex matching and event construction only. Each handler extracts raw
 * captured groups (display name, coin totals, amounts) and posts the event as-is.
 * Product ID resolution, tax arithmetic, and price derivation belong to consumers.
 */
@Module
public class BazaarChatHandler extends BUListener {
    @Subscription(priority = Subscription.HIGHEST)
    public void onChat(ChatReceivedEvent.Pre event) {
        String message = Util.stripFormatCodes(event.getComponent().getString());

        if (message.contains("Error")) {
            Util.logMessage("Chat message filtered (contains 'Error'): %s".formatted(message));

            return;
        }

        RegexSwitch.when()
                .on(ORDER_FLIPPED, BazaarChatHandler::handleBuyOrderFlip)
                .on(BUY_ORDER_CREATED, BazaarChatHandler::handleBuyOrderCreated)
                .on(SELL_OFFER_CREATED, BazaarChatHandler::handleSellOfferCreated)
                .on(BUY_ORDER_CANCELLED, BazaarChatHandler::handleBuyOrderCancelled)
                .on(SELL_OFFER_CANCELLED, BazaarChatHandler::handleSellOfferCancelled)
                .on(BUY_ORDER_FILLED, BazaarChatHandler::handleFilledBuyOrder)
                .on(SELL_OFFER_FILLED, BazaarChatHandler::handleFilledSellOffer)
                .on(BUY_ORDER_CLAIMED, BazaarChatHandler::handleBuyOrderClaim)
                .on(SELL_OFFER_CLAIMED, BazaarChatHandler::handleSellOfferClaim)
                .on(INSTANT_BUY, BazaarChatHandler::handleInstantBuyAction)
                .on(INSTANT_SELL, BazaarChatHandler::handleInstantSellAction)
                .against(message);
    }

    private static final Pattern BUY_ORDER_CREATED = Pattern.compile("Buy Order Setup! (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");

    private static void handleBuyOrderCreated(Matcher matcher) {
        Util.logMessage("BUY_ORDER_CREATED — item=%s price=%s amount=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount")));

        post(new BazaarChatEvent.BuyOrderCreated(now(), clean(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount"))));
    }

    private static final Pattern SELL_OFFER_CREATED = Pattern.compile("Sell Offer Setup! (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");

    private static void handleSellOfferCreated(Matcher matcher) {
        Util.logMessage("SELL_OFFER_CREATED — item=%s price=%s amount=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount")));

        post(new BazaarChatEvent.SellOfferCreated(now(), clean(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount"))));
    }

    private static final Pattern BUY_ORDER_CANCELLED = Pattern.compile("Cancelled! Refunded (?<coins>[\\d,.]+) coins from cancelling Buy Order");

    private static void handleBuyOrderCancelled(Matcher matcher) {
        Util.logMessage("BUY_ORDER_CANCELLED — coins=%s".formatted(matcher.group("coins")));

        post(new BazaarChatEvent.BuyOrderCancelled(now(), coins(matcher.group("coins"))));
    }

    private static final Pattern SELL_OFFER_CANCELLED = Pattern.compile("Cancelled! Refunded (?<amount>[\\d,]+)x (?<item>.+?) from cancelling Sell Offer");

    private static void handleSellOfferCancelled(Matcher matcher) {
        Util.logMessage("SELL_OFFER_CANCELLED — item=%s amount=%s".formatted(matcher.group("item"), matcher.group("amount")));

        post(new BazaarChatEvent.SellOfferCancelled(now(), clean(matcher.group("item")), amount(matcher.group("amount"))));
    }

    private static final Pattern BUY_ORDER_FILLED = Pattern.compile("Your Buy Order for (?<amount>[\\d,]+)x (?<item>.+?) was filled");

    private static void handleFilledBuyOrder(Matcher matcher) {
        Util.logMessage("BUY_ORDER_FILLED — item=%s amount=%s".formatted(matcher.group("item"), matcher.group("amount")));

        post(new BazaarChatEvent.BuyOrderFilled(now(), clean(matcher.group("item")), amount(matcher.group("amount"))));
    }

    private static final Pattern SELL_OFFER_FILLED = Pattern.compile("Your Sell Offer for (?<amount>[\\d,]+)x (?<item>.+?) was filled");

    private static void handleFilledSellOffer(Matcher matcher) {
        Util.logMessage("SELL_OFFER_FILLED — item=%s amount=%s".formatted(matcher.group("item"), matcher.group("amount")));

        post(new BazaarChatEvent.SellOfferFilled(now(), clean(matcher.group("item")), amount(matcher.group("amount"))));
    }

    private static final Pattern BUY_ORDER_CLAIMED = Pattern.compile("Claimed (?<amount>[\\d,]+)x (?<item>.+?) worth (?<coins>[\\d,.]+) coins bought for (?<price>[\\d,.]+) each");

    private static void handleBuyOrderClaim(Matcher matcher) {
        Util.logMessage("BUY_ORDER_CLAIMED — item=%s price=%s amount=%s coins=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount"), matcher.group("coins")));

        post(new BazaarChatEvent.BuyOrderClaimed(now(), clean(matcher.group("item")), amount(matcher.group("amount")), coins(matcher.group("coins")), coins(matcher.group("price"))));
    }

    private static final Pattern SELL_OFFER_CLAIMED = Pattern.compile("Claimed (?<coins>[\\d,.]+) coins from selling (?<amount>[\\d,]+)x (?<item>.+?) at (?<price>[\\d,.]+) each");

    private static void handleSellOfferClaim(Matcher matcher) {
        Util.logMessage("SELL_OFFER_CLAIMED — item=%s price=%s amount=%s coins=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount"), matcher.group("coins")));

        post(new BazaarChatEvent.SellOfferClaimed(now(), clean(matcher.group("item")), amount(matcher.group("amount")), coins(matcher.group("coins")), coins(matcher.group("price"))));
    }

    private static final Pattern INSTANT_BUY = Pattern.compile("Bought (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");

    private static void handleInstantBuyAction(Matcher matcher) {
        Util.logMessage("INSTANT_BUY — item=%s price=%s amount=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount")));

        post(new BazaarChatEvent.InstantBuy(now(), clean(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount"))));
    }

    private static final Pattern INSTANT_SELL = Pattern.compile("Sold (?<amount>[\\d,]+)x (?<item>.+?) for (?<coins>[\\d,.]+) coins");

    private static void handleInstantSellAction(Matcher matcher) {
        Util.logMessage("INSTANT_SELL — item=%s amount=%s coins=%s".formatted(matcher.group("item"), matcher.group("amount"), matcher.group("coins")));

        post(new BazaarChatEvent.InstantSell(now(), clean(matcher.group("item")), coins(matcher.group("coins")), amount(matcher.group("amount"))));
    }

    private static final Pattern ORDER_FLIPPED = Pattern.compile("Order Flipped! (?<amount>[\\d,]+)x (?<item>.+?) for (?<profit>-?[\\d,.]+) coins of total expected profit");

    private static void handleBuyOrderFlip(Matcher matcher) {
        Util.logMessage("ORDER_FLIPPED — item=%s profit=%s amount=%s".formatted(matcher.group("item"), matcher.group("profit"), matcher.group("amount")));

        post(new BazaarChatEvent.BuyOrderFlipped(now(), clean(matcher.group("item")), amount(matcher.group("amount")), coins(matcher.group("profit"))));
    }

    private static void post(BazaarChatEvent event) {
        event.post(EVENT_BUS);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String clean(String s) {
        return Util.removeFormatting(s.trim());
    }

    private static int amount(String s) {
        return Integer.parseInt(s.replace(",", "").trim());
    }

    private static double coins(String s) {
        return Double.parseDouble(s.replace(",", "").trim());
    }
}