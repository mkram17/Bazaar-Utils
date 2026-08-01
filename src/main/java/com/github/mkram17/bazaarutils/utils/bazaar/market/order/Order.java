package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.events.AbstractListener;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.features.notification.OutbidOrderHandler;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.List;
import java.util.Optional;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//TODO figure out how to handle rounding with price
//TODO use last viewed item in bazaar to help with finding accurate price instead of just chat message
/**
 * Extension of {@link OrderInfo} that tracks live Bazaar orders and reacts to events such
 * as outbids, user order changes, and price updates.
 */
@ToString(callSuper=true)
public class Order extends OrderInfo implements AbstractListener {
    public static final Codec<Order> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING
                    .fieldOf("name")
                    .forGetter(Order::getName),
            Codec.INT
                    .fieldOf("volume")
                    .forGetter(Order::getVolume),
            Codec.DOUBLE
                    .fieldOf("price_per_item")
                    .forGetter(Order::getPricePerItem),
            TransactionType.Side.CODEC
                    .fieldOf("side")
                    .forGetter(order -> order.getTransactionType().getSide()),
            Codec.INT
                    .optionalFieldOf("amount_claimed", 0)
                    .forGetter(Order::getAmountClaimed),
            Codec.INT
                    .optionalFieldOf("amount_filled", 0)
                    .forGetter(Order::getAmountFilled),
            // Written out only. Decoding still re-derives it in the OrderInfo constructor, which resolves
            // it from BazaarDataUtil.
            Codec.STRING
                    .optionalFieldOf("product_id", "")
                    .forGetter(order -> order.getProductID() == null ? "" : order.getProductID())
    ).apply(instance, (name, volume, pricePerItem, side, amountClaimed, amountFilled, productId) -> {
        Order order = new Order(name, volume, pricePerItem, side, null);
        order.setAmountClaimed(amountClaimed);
        order.setAmountFilled(amountFilled); // restores OrderStatus via existing logic
        return order;
    }));

    public static final int OUTBID_ORDER_NOTIFICATIONS = 3; // number of notifications to send when an order becomes outdated

    @Getter @Setter
    private int amountClaimed = 0;
    @Getter
    private int amountFilled = 0;

    /**
     * Creates a Bazaar order, initializing ItemInfo with slot index and ItemStack of the order.
     */
    public Order(@NonNull String name, int volume, double pricePerItem, TransactionType.Side side, @Nullable ItemInfo itemInfo) {
        super(name, side, OrderStatus.SET, volume, pricePerItem, itemInfo);

        startTracking();
    }

    @Override
    public void subscribe() {
        EVENT_BUS.register(this);
    }

    private void startTracking() {
        handleOutbidStatusChange();
        subscribe();
    }

    @Subscription
    private void onDataUpdate(BazaarDataUpdateEvent event) {
        handleOutbidStatusChange();
    }

    @Subscription
    private void onUserOrderChange(UserOrdersChangeEvent event) {
        if (event.getChangeType() == UserOrdersChangeEvent.ChangeTypes.REMOVE || event.getOrder() != this) {
            return;
        }

        handleOutbidStatusChange();
    }

    private void handleOutbidStatusChange() {
        Optional<PricingPosition> pricingPositionOptional = findPricingPosition();

        if (pricingPositionOptional.isEmpty()) {
            return;
        }

        PricingPosition newPosition = pricingPositionOptional.get();

        if (this.pricingPosition != newPosition) {
            this.pricingPosition = newPosition;
            onOutbid(newPosition == PricingPosition.OUTBID);
        }
    }

    private void onOutbid(boolean isOutbid) {
        NotificationsConfig.NotificationSettings settings = NotificationsConfig.ORDER_NOTIFICATIONS_OUTBID;

        boolean shouldNotifyUser = settings.isEnabled() && settings.emitChatMessage;
        boolean shouldPlayNotificationSound = settings.isEnabled() && settings.emitClientSound;
        boolean shouldAutoOpenBazaar = settings.isEnabled() && settings.emitClientSound;

        if (!shouldNotifyUser || !OrderUtil.getUserOrders().contains(this)) {
            return;
        }

        if (getStatus() == OrderStatus.FILLED) {
            return;
        }

        MutableComponent message;

        if (isOutbid) {
            message = OutbidOrderHandler.getOutbidMessage(this);

            if (DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
                message.append(Component.literal(". Market Price: " + this.getMarketPrice(this.getTransactionType().getSide()) + " Order Price: " + this.getPricePerItem()));
            }

            if (shouldAutoOpenBazaar) {
                OrderUtil.openBazaar();
            }

            Minecraft client = Minecraft.getInstance();

            var player = client.player;

            if (shouldPlayNotificationSound && player != null) {
                SoundUtil.notifyMultipleTimes(OUTBID_ORDER_NOTIFICATIONS);
            }

            Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyChatCommand(message, "managebazaarorders"));
        } else if (getPricingPosition() == PricingPosition.COMPETITIVE) {
            message = OutbidOrderHandler.getCompetitiveMessage(this);
            Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyAll(message));
        } else {
            message = OutbidOrderHandler.getMatchedMessage(this);
            Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyAll(message));
        }
    }


    /**
     * @return index of this order within the persisted user order list.
     */
    public int getIndex() {
        return OrderUtil.getUserOrders().indexOf(this);
    }

    /**
     * Updates the tracked filled amount and automatically marks the order as filled when the volume is reached.
     */
    public void setAmountFilled(int amountFilled) {
        this.amountFilled = amountFilled;

        if (this.amountFilled >= volume) {
            setFilled();
        } else {
            this.status = OrderStatus.SET;
        }
    }

    /**
     * Marks the order as fully filled and syncs the filled amount with the expected volume.
     */
    public void setFilled() {
        this.amountFilled = volume;
        this.status = OrderStatus.FILLED;
    }

    /**
     * Removes this order from the tracked user orders list and notifies listeners.
     */
    public void removeFromUserOrders() {
        List<Order> userOrders = UserOrdersStorage.INSTANCE.get();

        if (userOrders == null || !userOrders.remove(this)) {
            PlayerActionUtil.notifyAll("Error removing " + name + " from user orders. Item couldn't be found.");
        }

        new UserOrdersChangeEvent(this, UserOrdersChangeEvent.ChangeTypes.REMOVE).post(EVENT_BUS);

        // Stop tracking: without this the removed order stays subscribed and keeps reacting to
        // every BazaarDataUpdateEvent for the rest of the session (leak). Safe no-op if never registered.
        EVENT_BUS.unregister(this);

        UserOrdersStorage.INSTANCE.save();
    }
}
