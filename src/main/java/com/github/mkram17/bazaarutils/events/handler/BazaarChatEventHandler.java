package com.github.mkram17.bazaarutils.events.handler;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.events.BazaarChatEvent;
import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import meteordevelopment.orbit.EventHandler;

import java.util.Optional;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Handler for all BazaarChatEvent occurrences.
 * <p>
 * This class processes bazaar-related chat events and performs various actions such as:
 * </p>
 * <ul>
 *   <li>Tracking order creation and adding orders to watch lists</li>
 *   <li>Playing notification sounds when orders are filled</li>
 *   <li>Updating the order limit tracker for instant buys and sells</li>
 *   <li>Marking orders as filled in the internal order tracking system</li>
 * </ul>
 * 
 * <p>The handler automatically registers itself during mod initialization via the
 * {@link RunOnInit} annotation.</p>
 * 
 * @see BazaarChatEvent
 * @see Order
 * @see OrderInfo
 */
public class BazaarChatEventHandler {

    /**
     * Number of notification sounds to play when an order is filled.
     */
    public static final int ORDER_FILLED_NOTIFICATIONS = 2;

    /**
     * General handler that fires for any bazaar chat event.
     * Sends a notification with the event type.
     */
    @EventHandler
    private static void onAnyOrder(BazaarChatEvent<? extends OrderInfo> event) {
//        SoundUtil.notifyMultipleTimes(4);
        PlayerActionUtil.notifyAll("Bazaar Order: " + event.type().name(), NotificationType.ORDERDATA);
    }

    /**
     * Handles order creation events.
     * Updates the order limit tracker and adds the order to the watched orders list.
     */
    @EventHandler
    private static void onOrderCreated(BazaarChatEvent<? extends OrderInfo> event) {
        if (!(event.type() == BazaarChatEvent.BazaarEventTypes.ORDER_CREATED) || !(event.order() instanceof Order order)) {
            return;
        }

        BazaarLimitsVisualizer.addOrderToLimit(order.getVolume()* order.getPricePerItem());

        Util.addWatchedOrder(order);
        //for some reason 52800046 for 4 was on hypixel as 13200011.6 but calculates to 13200011.5. current theory is that buy price wasnt fully accurate, and it rounded up. also was .2 off on sell order for it. obviously problems with big prices
    }
    /**
     * Handles instant sell events.
     * Updates the order limit tracker with the pre-tax price.
     * Note: Chat shows price before tax, but actual transaction includes tax.
     */
    @EventHandler
    private static void onInstaSell(BazaarChatEvent<? extends OrderInfo> event) {
        if (!(event.type() == BazaarChatEvent.BazaarEventTypes.INSTA_SELL)) {
            return;
        }

        OrderInfo order = event.order();

        //insta sell shows the price before tax in chat, but it actually costs more than that
        double totalPriceBeforeTax = order.getVolume()*order.getPricePerItem();
        double totalPriceWithTax = totalPriceBeforeTax * ((100 + BUConfig.userBazaarTax)/100);

        //order limit does not count the tax
        BazaarLimitsVisualizer.addOrderToLimit(totalPriceBeforeTax);

        PlayerActionUtil.notifyAll("Insta sell for " + order, NotificationType.FEATURE);
    }
    /**
     * Handles instant buy events.
     * Updates the order limit tracker with the total purchase price.
     */
    @EventHandler
    private static void onInstaBuy(BazaarChatEvent<? extends OrderInfo> event) {
        if (!(event.type() == BazaarChatEvent.BazaarEventTypes.INSTA_BUY)) {
            return;
        }

        OrderInfo order = event.order();

        double totalPrice = order.getVolume() * order.getPricePerItem();

        BazaarLimitsVisualizer.addOrderToLimit(totalPrice);

        PlayerActionUtil.notifyAll("Insta buy for " + order, NotificationType.FEATURE);
    }

    /**
     * Handles order filled events.
     * Plays notification sounds if enabled, marks the order as filled, and notifies the player.
     */
    @EventHandler
    private static void onOrderFilled(BazaarChatEvent<? extends OrderInfo> event) {
        if (!(event.type() == BazaarChatEvent.BazaarEventTypes.ORDER_FILLED)) {
            return;
        }

        if (BUConfig.get().feature.orderFilledNotificationSound.isEnabled()) {
            SoundUtil.notifyMultipleTimes(ORDER_FILLED_NOTIFICATIONS);
        }

        OrderInfo order = event.order();

        Optional<Order> orderMatch = order.findOrderInList(BUConfig.get().general.userOrders);

        if (orderMatch.isPresent()) {
            orderMatch.get().setFilled();
            PlayerActionUtil.notifyAll(order.getName() + "[" + orderMatch.get().getIndex() + "] was filled", NotificationType.ORDERDATA);
        } else {
            Util.notifyError("Could not find item to fill with info vol: " + order.getVolume() + " name: " + order.getName(), new Exception("Order Filled Event error"));
        }
    }

    @RunOnInit
    public static void subscribe() {
        EVENT_BUS.subscribe(BazaarChatEventHandler.class);
    }
}
