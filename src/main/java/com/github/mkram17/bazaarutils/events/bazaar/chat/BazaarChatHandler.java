package com.github.mkram17.bazaarutils.events.bazaar.chat;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.RegexSwitch;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Matches incoming Hypixel Bazaar chat messages against anchored patterns and posts the
 * corresponding {@link BazaarChatEvent} subtype.
 *
 * <p>Owns pattern matching and raw value extraction only. Handler methods extract the
 * literal captured groups — display names, coin totals, item counts — and post the event
 * with no further interpretation. Product ID resolution, tax arithmetic, price derivation,
 * and order matching are deferred to the consuming data sources.
 *
 * <p>All Hypixel Bazaar chat notifications begin with {@code "[Bazaar] "}; messages
 * without that prefix (including public, party, guild, and direct-message chat) are
 * fast-rejected before any regex runs, and all patterns are anchored on {@code ^\[Bazaar\] }.
 */
@Module
public final class BazaarChatHandler extends BUListener {
    private static final String BAZAAR_PREFIX = "[Bazaar] ";

    private static final Pattern BUY_ORDER_CREATED = Pattern.compile("^\\[Bazaar\\] Buy Order Setup! (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");
    private static final Pattern SELL_OFFER_CREATED = Pattern.compile("^\\[Bazaar\\] Sell Offer Setup! (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");
    private static final Pattern BUY_ORDER_CANCELLED = Pattern.compile("^\\[Bazaar\\] Cancelled! Refunded (?<coins>[\\d,.]+) coins from cancelling Buy Order");
    private static final Pattern SELL_OFFER_CANCELLED = Pattern.compile("^\\[Bazaar\\] Cancelled! Refunded (?<amount>[\\d,]+)x (?<item>.+?) from cancelling Sell Offer");
    private static final Pattern BUY_ORDER_FILLED = Pattern.compile("^\\[Bazaar\\] Your Buy Order for (?<amount>[\\d,]+)x (?<item>.+?) was filled");
    private static final Pattern SELL_OFFER_FILLED = Pattern.compile("^\\[Bazaar\\] Your Sell Offer for (?<amount>[\\d,]+)x (?<item>.+?) was filled");
    private static final Pattern BUY_ORDER_CLAIMED = Pattern.compile("^\\[Bazaar\\] Claimed (?<amount>[\\d,]+)x (?<item>.+?) worth (?<coins>[\\d,.]+) coins bought for (?<price>[\\d,.]+) each");
    private static final Pattern SELL_OFFER_CLAIMED = Pattern.compile("^\\[Bazaar\\] Claimed (?<coins>[\\d,.]+) coins from selling (?<amount>[\\d,]+)x (?<item>.+?) at (?<price>[\\d,.]+) each");
    private static final Pattern INSTANT_BUY = Pattern.compile("^\\[Bazaar\\] Bought (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");
    private static final Pattern INSTANT_SELL = Pattern.compile("^\\[Bazaar\\] Sold (?<amount>[\\d,]+)x (?<item>.+?) for (?<coins>[\\d,.]+) coins");
    private static final Pattern BUY_ORDER_FLIPPED = Pattern.compile("^\\[Bazaar\\] Order Flipped! (?<amount>[\\d,]+)x (?<item>.+?) for (?<profit>-?[\\d,.]+) coins of total expected profit");

    private static final RegexSwitch.Switch DISPATCH = RegexSwitch.when()
            .on(BUY_ORDER_FLIPPED, BazaarChatHandler::handleBuyOrderFlip)
            .on(BUY_ORDER_CREATED, BazaarChatHandler::handleBuyOrderCreated)
            .on(SELL_OFFER_CREATED, BazaarChatHandler::handleSellOfferCreated)
            .on(BUY_ORDER_CANCELLED, BazaarChatHandler::handleBuyOrderCancelled)
            .on(SELL_OFFER_CANCELLED, BazaarChatHandler::handleSellOfferCancelled)
            .on(BUY_ORDER_FILLED, BazaarChatHandler::handleFilledBuyOrder)
            .on(SELL_OFFER_FILLED, BazaarChatHandler::handleFilledSellOffer)
            .on(BUY_ORDER_CLAIMED, BazaarChatHandler::handleBuyOrderClaim)
            .on(SELL_OFFER_CLAIMED, BazaarChatHandler::handleSellOfferClaim)
            .on(INSTANT_BUY, BazaarChatHandler::handleInstantBuyAction)
            .on(INSTANT_SELL, BazaarChatHandler::handleInstantSellAction);

    @Subscription(priority = Priority.HIGHEST)
    @OnlyOnSkyBlock
    public void onChat(ChatReceivedEvent.Pre event) {
        String message = Util.stripFormatCodes(event.getComponent().getString());
        if (!message.startsWith(BAZAAR_PREFIX)) return;

        if (message.contains("Error")) {
            Util.logMessage("Chat message filtered (contains 'Error'): %s".formatted(message));
            return;
        }

        try {
            DISPATCH.against(message);
        } catch (NumberFormatException exception) {
            Util.logError("Failed to parse numeric capture in Bazaar chat message: %s".formatted(message), exception);
        }
    }

    private static void handleBuyOrderCreated(Matcher matcher) {
        Util.logMessage("BUY_ORDER_CREATED — item=%s price=%s amount=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount")));

        post(new BazaarChatEvent.BuyOrderCreated(now(), itemName(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount"))));
    }

    private static void handleSellOfferCreated(Matcher matcher) {
        Util.logMessage("SELL_OFFER_CREATED — item=%s price=%s amount=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount")));

        post(new BazaarChatEvent.SellOfferCreated(now(), itemName(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount"))));
    }

    private static void handleBuyOrderCancelled(Matcher matcher) {
        Util.logMessage("BUY_ORDER_CANCELLED — coins=%s".formatted(matcher.group("coins")));

        post(new BazaarChatEvent.BuyOrderCancelled(now(), coins(matcher.group("coins"))));
    }

    private static void handleSellOfferCancelled(Matcher matcher) {
        Util.logMessage("SELL_OFFER_CANCELLED — item=%s amount=%s".formatted(matcher.group("item"), matcher.group("amount")));

        post(new BazaarChatEvent.SellOfferCancelled(now(), itemName(matcher.group("item")), amount(matcher.group("amount"))));
    }

    private static void handleFilledBuyOrder(Matcher matcher) {
        Util.logMessage("BUY_ORDER_FILLED — item=%s amount=%s".formatted(matcher.group("item"), matcher.group("amount")));

        post(new BazaarChatEvent.BuyOrderFilled(now(), itemName(matcher.group("item")), amount(matcher.group("amount"))));
    }

    private static void handleFilledSellOffer(Matcher matcher) {
        Util.logMessage("SELL_OFFER_FILLED — item=%s amount=%s".formatted(matcher.group("item"), matcher.group("amount")));

        post(new BazaarChatEvent.SellOfferFilled(now(), itemName(matcher.group("item")), amount(matcher.group("amount"))));
    }

    private static void handleBuyOrderClaim(Matcher matcher) {
        Util.logMessage("BUY_ORDER_CLAIMED — item=%s price=%s amount=%s coins=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount"), matcher.group("coins")));

        post(new BazaarChatEvent.BuyOrderClaimed(now(), itemName(matcher.group("item")), amount(matcher.group("amount")), coins(matcher.group("coins")), coins(matcher.group("price"))));
    }

    private static void handleSellOfferClaim(Matcher matcher) {
        Util.logMessage("SELL_OFFER_CLAIMED — item=%s price=%s amount=%s coins=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount"), matcher.group("coins")));

        post(new BazaarChatEvent.SellOfferClaimed(now(), itemName(matcher.group("item")), amount(matcher.group("amount")), coins(matcher.group("coins")), coins(matcher.group("price"))));
    }

    private static void handleInstantBuyAction(Matcher matcher) {
        Util.logMessage("INSTANT_BUY — item=%s price=%s amount=%s".formatted(matcher.group("item"), matcher.group("price"), matcher.group("amount")));

        post(new BazaarChatEvent.InstantBuy(now(), itemName(matcher.group("item")), coins(matcher.group("price")), amount(matcher.group("amount"))));
    }

    private static void handleInstantSellAction(Matcher matcher) {
        Util.logMessage("INSTANT_SELL — item=%s amount=%s coins=%s".formatted(matcher.group("item"), matcher.group("amount"), matcher.group("coins")));

        post(new BazaarChatEvent.InstantSell(now(), itemName(matcher.group("item")), coins(matcher.group("coins")), amount(matcher.group("amount"))));
    }

    private static void handleBuyOrderFlip(Matcher matcher) {
        Util.logMessage("ORDER_FLIPPED — item=%s profit=%s amount=%s".formatted(matcher.group("item"), matcher.group("profit"), matcher.group("amount")));

        post(new BazaarChatEvent.BuyOrderFlipped(now(), itemName(matcher.group("item")), amount(matcher.group("amount")), coins(matcher.group("profit"))));
    }

    private static void post(BazaarChatEvent event) {
        event.post(EVENT_BUS);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String itemName(String s) {
        return s.trim();
    }

    private static int amount(String s) {
        return Integer.parseInt(s.replace(",", "").trim());
    }

    private static double coins(String s) {
        return Double.parseDouble(s.replace(",", "").trim());
    }
}
