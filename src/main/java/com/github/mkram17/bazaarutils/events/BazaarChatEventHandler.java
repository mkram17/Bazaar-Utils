package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.storage.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarChatEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.Optional;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Consumer of {@link BazaarChatEvent}: this handler <em>reacts to</em> the bazaar chat events that
 * {@code BazaarChatHandler} parses and posts — it does not read chat itself. (The names are
 * confusingly close: {@code BazaarChatHandler} is the producer, {@code BazaarChatEventHandler} is
 * the consumer.)
 * <p>
 * It performs various actions in response, such as:
 * </p>
 * <ul>
 *   <li>Tracking order creation and adding orders to watch lists</li>
 *   <li>Playing notification sounds when orders are filled</li>
 *   <li>Updating the order limit tracker for instant buys and sells</li>
 *   <li>Marking orders as filled in the internal order tracking system</li>
 * </ul>
 *
 * <p>The handler registers automatically: it is annotated {@code @Module}, so the module
 * annotation pipeline collects it into the generated {@code BazaarUtilsModules} registry and
 * constructs it during initialization, at which point {@link BUListener} subscribes it.</p>
 *
 * @see BazaarChatEvent
 * @see Order
 * @see OrderInfo
 */
@Module
public final class BazaarChatEventHandler extends BUListener {

    /**
     * Number of notification sounds to play when an order is filled.
     */
    public static final int ORDER_FILLED_NOTIFICATIONS = 2;

    /**
     * General handler that fires for any bazaar chat event.
     * Sends a notification with the event type.
     */
    @Subscription
    private void onAnyOrder(BazaarChatEvent<? extends OrderInfo> event) {
//        SoundUtil.notifyMultipleTimes(4);
        PlayerActionUtil.notifyAll("Bazaar Order: " + event.getType().name(), NotificationType.ORDERDATA);
    }

    /**
     * Handles order creation events.
     * Updates the order limit tracker and adds the order to the watched orders list.
     */
    @Subscription
    private void onOrderCreated(BazaarChatEvent<? extends OrderInfo> event) {
        if (!(event.getType() == BazaarChatEvent.BazaarEventTypes.ORDER_CREATED) || !(event.getOrder() instanceof Order order)) {
            return;
        }

        BazaarLimitsVisualizer.addOrderToLimit(order.getVolume()* order.getPricePerItem());

        OrderUtil.trackUserOrder(order);
        //for some reason 52800046 for 4 was on hypixel as 13200011.6 but calculates to 13200011.5. current theory is that buy price wasnt fully accurate, and it rounded up. also was .2 off on sell order for it. obviously problems with big prices
    }
    /**
     * Handles instant sell events.
     * Updates the order limit tracker with the pre-tax price.
     * Note: Chat shows price before tax, but actual transaction includes tax.
     */
    @Subscription
    private void onInstaSell(BazaarChatEvent<? extends OrderInfo> event) {
        if (!(event.getType() == BazaarChatEvent.BazaarEventTypes.INSTA_SELL)) {
            return;
        }

        OrderInfo order = event.getOrder();

        //insta sell shows the price before tax in chat, but it actually costs more than that
        double totalPriceBeforeTax = order.getVolume()*order.getPricePerItem();
        double totalPriceWithTax = totalPriceBeforeTax * ((100 + BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE.userBazaarTax) / 100);

        //order limit does not count the tax
        BazaarLimitsVisualizer.addOrderToLimit(totalPriceBeforeTax);

        PlayerActionUtil.notifyAll("Insta sell for " + order, NotificationType.FEATURE);
    }
    /**
     * Handles instant buy events.
     * Updates the order limit tracker with the total purchase price.
     */
    @Subscription
    private void onInstaBuy(BazaarChatEvent<? extends OrderInfo> event) {
        if (!(event.getType() == BazaarChatEvent.BazaarEventTypes.INSTA_BUY)) {
            return;
        }

        OrderInfo order = event.getOrder();

        double totalPrice = order.getVolume() * order.getPricePerItem();

        BazaarLimitsVisualizer.addOrderToLimit(totalPrice);

        PlayerActionUtil.notifyAll("Insta buy for " + order, NotificationType.FEATURE);
    }

    /**
     * Handles order filled events.
     * Plays notification sounds if enabled, marks the order as filled, and notifies the player.
     */
    @Subscription
    private void onOrderFilled(BazaarChatEvent<? extends OrderInfo> event) {
        if (!(event.getType() == BazaarChatEvent.BazaarEventTypes.ORDER_FILLED)) {
            return;
        }

        NotificationsConfig.NotificationSettings settings = NotificationsConfig.ORDER_NOTIFICATIONS_FILLED;

        if (settings.isEnabled()) {
            SoundUtil.notifyMultipleTimes(ORDER_FILLED_NOTIFICATIONS);
        }

        OrderInfo order = event.getOrder();

        Optional<Order> orderMatch = order.findOrderInList(UserOrdersStorage.INSTANCE.get());

        if (orderMatch.isPresent()) {
            Order filled = orderMatch.get();

            filled.setFilled();
            PlayerActionUtil.notifyAll(order.getName() + "[" + filled.getIndex() + "] was filled", NotificationType.ORDERDATA);
            UserOrdersStorage.INSTANCE.save();

            // The list did not gain or lose an entry, so nothing else announces this. Both fields
            // setFilled() touches — status and amountFilled — are pushed to the website, and
            // without an event the change would sit unsent until the player reopens Manage Orders.
            new UserOrdersChangeEvent(filled, UserOrdersChangeEvent.ChangeTypes.UPDATE).post(EVENT_BUS);
        } else {
            Util.notifyError("Could not find item to fill with info vol: " + order.getVolume() + " name: " + order.getName(), new Exception("Order Filled Event error"));
        }
    }

    /**
     * Drops a cancelled order from the tracked list.
     *
     * <p>Nothing consumed this event before, so a cancelled order stayed in the list — and on the
     * website, which reads absence from a snapshot as the only signal an order has closed — until
     * the player next opened Manage Orders and {@code OrderUpdater} reconciled it away.</p>
     *
     * <p>Matching goes through {@link OrderInfo#findOrderInList}, which tries a strict pass and
     * falls back to a loose one. That can pick the wrong order among near-identical ones, and
     * that is the intended trade: a wrong guess is corrected by the next Manage Orders visit,
     * whereas refusing to guess leaves a cancelled order showing as live indefinitely.</p>
     */
    @Subscription
    private void onOrderCancelled(BazaarChatEvent<? extends OrderInfo> event) {
        if (event.getType() != BazaarChatEvent.BazaarEventTypes.ORDER_CANCELLED) {
            return;
        }

        OrderInfo cancelled = event.getOrder();
        Optional<Order> match = cancelled.findOrderInList(UserOrdersStorage.INSTANCE.get());

        if (match.isEmpty()) {
            Util.notifyError("Could not find cancelled order vol: " + cancelled.getVolume() + " name: " + cancelled.getName(),
                    new Exception("Order Cancelled Event error"));

            return;
        }

        // Posts REMOVE and saves for us.
        match.get().removeFromUserOrders();
        PlayerActionUtil.notifyAll("Cancelled " + cancelled.getName(), NotificationType.ORDERDATA);
    }

    /**
     * Records that a filled order's proceeds have been collected.
     *
     * <p>The Bazaar has no partial claim — clicking an order takes everything currently filled and
     * unclaimed — so after a claim the claimed amount is by definition the filled amount. That is
     * the same identity {@code OrderUpdater.parseAmountClaimed} arrives at from lore, where it
     * reads the unclaimed remainder and subtracts.</p>
     *
     * <p>{@code ORDER_CLAIMED} is one of the two events that carries a real {@link Order} resolved
     * against storage rather than a bare {@link OrderInfo}, so there is nothing to match here.</p>
     */
    @Subscription
    private void onOrderClaimed(BazaarChatEvent<? extends OrderInfo> event) {
        if (event.getType() != BazaarChatEvent.BazaarEventTypes.ORDER_CLAIMED
                || !(event.getOrder() instanceof Order order)) {
            return;
        }

        if (order.getAmountClaimed() == order.getAmountFilled()) {
            return;
        }

        order.setAmountClaimed(order.getAmountFilled());
        UserOrdersStorage.INSTANCE.save();

        new UserOrdersChangeEvent(order, UserOrdersChangeEvent.ChangeTypes.UPDATE).post(EVENT_BUS);
    }
}
