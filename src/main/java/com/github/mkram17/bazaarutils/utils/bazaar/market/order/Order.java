package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.events.listener.AbstractListener;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.features.notification.OutbidOrderHandler;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
    public static final int OUTBID_ORDER_NOTIFICATIONS = 3; // number of notifications to send when an order becomes outdated

    @Getter @Setter
    private int amountClaimed = 0;
    @Getter
    private int amountFilled = 0;

    /**
     * Creates a Bazaar order, initializing ItemInfo with slot index and ItemStack of the order.
     */
    public Order(@NonNull String name, int volume, double pricePerItem, @NonNull OrderType orderType, @Nullable ItemInfo itemInfo) {
        super(name, orderType, OrderStatus.SET, volume, pricePerItem, itemInfo);

        startTracking();
    }

    @Override
    public void subscribe() {
        EVENT_BUS.subscribe(this);
    }

    private void startTracking() {
        handleOutbidStatusChange();
        subscribe();
    }

    @EventHandler
    private void onDataUpdate(BazaarDataUpdateEvent event) {
        handleOutbidStatusChange();
    }

    @EventHandler
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

        if (!shouldNotifyUser || !UserOrdersStorage.INSTANCE.get().contains(this)) {
            return;
        }

        if (getStatus() == OrderStatus.FILLED) {
            return;
        }

        MutableText message;

        if (isOutbid) {
            message = OutbidOrderHandler.getOutbidMessage(this);

            if (DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
                message.append(Text.literal(". Market Price: " + this.getMarketPrice(this.getOrderType()) + " Order Price: " + this.getPricePerItem()));
            }

            if (shouldAutoOpenBazaar) {
                OrderUtil.openBazaar();
            }

            MinecraftClient client = MinecraftClient.getInstance();

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
        return UserOrdersStorage.INSTANCE.get().indexOf(this);
    }

    public double getMarketPrice(OrderType orderType) {
        return OrderUtil.getPriceForPosition(productID, PricingPosition.MATCHED, orderType);
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
        if (!UserOrdersStorage.INSTANCE.get().remove(this)) {
            PlayerActionUtil.notifyAll("Error removing " + name + " from user orders. Item couldn't be found.");
        }

        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.REMOVE, this));

        UserOrdersStorage.INSTANCE.save();
    }
}
