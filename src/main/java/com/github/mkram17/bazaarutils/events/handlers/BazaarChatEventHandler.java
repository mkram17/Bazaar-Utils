package com.github.mkram17.bazaarutils.events.handlers;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import meteordevelopment.orbit.EventHandler;

import java.util.Optional;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public class BazaarChatEventHandler {

    public static final int ORDER_FILLED_NOTIFICATIONS = 2; // number of notifications to send when an order becomes outdated

    @EventHandler
    private static void onAnyOrder(BazaarChatEvent<? extends OrderInfoContainer> event) {
//        SoundUtil.notifyMultipleTimes(4);
        PlayerActionUtil.notifyAll("Bazaar Order: " + event.type().name(), Util.notificationTypes.ORDERDATA);
    }

    @EventHandler
    private static void onOrderCreated(BazaarChatEvent<? extends OrderInfoContainer> event) {
        if(!(event.type() == BazaarChatEvent.BazaarEventTypes.ORDER_CREATED) || !(event.order() instanceof BazaarOrder bazaarOrder)) return;
        BUConfig.get().orderLimit.addOrderToLimit(bazaarOrder.getVolume()*bazaarOrder.getPricePerItem());
        Util.addWatchedOrder(bazaarOrder);
        //for some reason 52800046 for 4 was on hypixel as 13200011.6 but calculates to 13200011.5. current theory is that buy price wasnt fully accurate, and it rounded up. also was .2 off on sell order for it. obviously problems with big prices
    }
    @EventHandler
    private static void onInstaSell(BazaarChatEvent<? extends OrderInfoContainer> event) {
        if(!(event.type() == BazaarChatEvent.BazaarEventTypes.INSTA_SELL))
            return;
        OrderInfoContainer order = event.order();
        //insta sell shows the price before tax in chat, but it actually costs more than that
        double totalPriceBeforeTax = order.getVolume()*order.getPricePerItem();
        double totalPriceWithTax = totalPriceBeforeTax * ((100 + BUConfig.get().bzTax)/100);

        //order limit does not count the tax
        BUConfig.get().orderLimit.addOrderToLimit(totalPriceBeforeTax);
        PlayerActionUtil.notifyAll("Insta sell for " + order, Util.notificationTypes.FEATURE);
    }
    @EventHandler
    private static void onInstaBuy(BazaarChatEvent<? extends OrderInfoContainer> event) {
        if (!(event.type() == BazaarChatEvent.BazaarEventTypes.INSTA_BUY))
            return;
        OrderInfoContainer order = event.order();
        double totalPrice = order.getVolume() * order.getPricePerItem();

        BUConfig.get().orderLimit.addOrderToLimit(totalPrice);
        PlayerActionUtil.notifyAll("Insta sell for " + order, Util.notificationTypes.FEATURE);
    }

    @EventHandler
    private static void onOrderFilled(BazaarChatEvent<? extends OrderInfoContainer> event) {
        if(!(event.type() == BazaarChatEvent.BazaarEventTypes.ORDER_FILLED))
            return;
        if (BUConfig.get().isOrderFilledSound()) {
            SoundUtil.notifyMultipleTimes(ORDER_FILLED_NOTIFICATIONS);
        }

        OrderInfoContainer order = event.order();
        Optional<BazaarOrder> orderMatch = order.findOrderInList(BUConfig.get().userOrders);
        if (orderMatch.isPresent()) {
            orderMatch.get().setFilled();
            PlayerActionUtil.notifyAll(order.getName() + "[" + orderMatch.get().getIndex() + "] was filled", Util.notificationTypes.ORDERDATA);
        } else {
            Util.notifyError("Could not find item to fill with info vol: " + order.getVolume() + " name: " + order.getName(), new Exception("Order Filled Event error"));
        }
    }

    @RunOnInit
    public static void subscribe() {
        EVENT_BUS.subscribe(BazaarChatEventHandler.class);
    }
}
