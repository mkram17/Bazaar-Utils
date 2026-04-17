package com.github.mkram17.bazaarutils.events.bazaar.handlers;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarChatEvent;
import com.github.mkram17.bazaarutils.utils.RegexSwitch;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.components.ChatOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Parses Bazaar-related chat messages and fires the appropriate {@link BazaarChatEvent}.
 *
 * <p>This class owns regex matching and event dispatch only. All price/tax/side
 * normalisation is delegated to {@link ChatOrderParser}.
 */
@Module
public class BazaarChatHandler extends BUListener {

    @Subscription(priority = Subscription.HIGHEST)
    public void onChat(ChatReceivedEvent.Pre event) {
        String message = Util.stripFormatCodes(event.getComponent().getString());

        if (message.contains("Error")) return;

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
        ChatOrderParser.parseBuyCreated(clean(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.BuyOrderCreated(order, now())));
    }

    private static final Pattern SELL_OFFER_CREATED = Pattern.compile("Sell Offer Setup! (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");

    private static void handleSellOfferCreated(Matcher matcher) {
        ChatOrderParser.parseSellCreated(clean(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.SellOfferCreated(order, now())));
    }

    private static final Pattern BUY_ORDER_FILLED = Pattern.compile("Your Buy Order for (?<amount>[\\d,]+)x (?<item>.+?) was filled");

    private static void handleFilledBuyOrder(Matcher matcher) {
        ChatOrderParser.parseFilled(clean(matcher.group("item")), TransactionType.Side.BUY, amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.BuyOrderFilled(order, now())));
    }

    private static final Pattern SELL_OFFER_FILLED = Pattern.compile("Your Sell Offer for (?<amount>[\\d,]+)x (?<item>.+?) was filled");

    private static void handleFilledSellOffer(Matcher matcher) {
        ChatOrderParser.parseFilled(clean(matcher.group("item")), TransactionType.Side.SELL, amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.SellOfferFilled(order, now())));
    }

    private static final Pattern BUY_ORDER_CLAIMED = Pattern.compile("Claimed (?<amount>[\\d,]+)x (?<item>.+?) worth (?<coins>[\\d,.]+) coins bought for (?<price>[\\d,.]+) each");

    private static void handleBuyOrderClaim(Matcher matcher) {
        ChatOrderParser.parseClaimedBuy(clean(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.BuyOrderClaimed(order, now())));
    }

    private static final Pattern SELL_OFFER_CLAIMED = Pattern.compile("Claimed (?<coins>[\\d,.]+) coins from selling (?<amount>[\\d,]+)x (?<item>.+?) at (?<price>[\\d,.]+) each");

    private static void handleSellOfferClaim(Matcher matcher) {
        ChatOrderParser.parseClaimedSell(clean(matcher.group("item")), coins(matcher.group("coins")), amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.SellOfferClaimed(order, now())));
    }

    private static final Pattern INSTANT_BUY = Pattern.compile("Bought (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");

    private static void handleInstantBuyAction(Matcher matcher) {
        ChatOrderParser.parseInstantBuy(clean(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.InstantBuy(order, now())));
    }

    private static final Pattern INSTANT_SELL = Pattern.compile("Sold (?<amount>[\\d,]+)x (?<item>.+?) for (?<coins>[\\d,.]+) coins");

    private static void handleInstantSellAction(Matcher matcher) {
        ChatOrderParser.parseInstantSell(clean(matcher.group("item")), coins(matcher.group("coins")), amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.InstantSell(order, now())));
    }

    private static final Pattern BUY_ORDER_CANCELLED = Pattern.compile("Cancelled! Refunded (?<coins>[\\d,.]+) coins from cancelling Buy Order");

    private static void handleBuyOrderCancelled(Matcher matcher) {
        post(new BazaarChatEvent.BuyOrderCancelled(coins(matcher.group("coins")), now()));
    }

    private static final Pattern SELL_OFFER_CANCELLED = Pattern.compile("Cancelled! Refunded (?<amount>[\\d,]+)x (?<item>.+?) from cancelling Sell Offer");

    private static void handleSellOfferCancelled(Matcher matcher) {
        ChatOrderParser.parseFilled(clean(matcher.group("item")), TransactionType.Side.SELL, amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.SellOfferCancelled(order, now())));
    }

    private static final Pattern ORDER_FLIPPED = Pattern.compile("Order Flipped! (?<amount>[\\d,]+)x (?<item>.+?) for (?<profit>-?[\\d,.]+) coins of total expected profit");

    private static void handleBuyOrderFlip(Matcher matcher) {
        ChatOrderParser.parseFlipped(clean(matcher.group("item")), coins(matcher.group("profit")), amount(matcher.group("amount")))
                .ifPresent(order -> post(new BazaarChatEvent.BuyOrderFlipped(order, now())));
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