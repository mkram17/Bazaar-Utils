package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.features.notification.OutbidOrderHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.ItemInfo;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;


//TODO figure out how to handle rounding with price
//TODO sometimes a BazaarOrder is created that is for the same order as one in userOrders, but not the same object. This causes problems sometimes, and should be fixed.
//TODO use last viewed item in bazaar to help with finding accurate price instead of just chat message
/**
 * Extension of {@link OrderInfo} that tracks live Bazaar orders and reacts to events such
 * as outbids, user order changes, and price updates.
 */
@Slf4j @ConfigObject
public class Order extends OrderInfo {
    public static final int OUTBID_ORDER_NOTIFICATIONS = 3; // number of notifications to send when an order becomes outdated

    @Getter @Setter @ConfigEntry(id = "amountClaimed")
    private int amountClaimed = 0;
    @Getter @ConfigEntry(id = "amountFilled")
    private int amountFilled = 0;

    /**
     * Creates a Bazaar order with no captured {@link ItemInfo} context.
     */
    public Order(String name, Integer volume, Double pricePerItem, OrderType orderType) {
        this(name, volume, pricePerItem, orderType, null);
    }

    /**
     * Creates a Bazaar order, initializing ItemInfo with slot index and ItemStack of the order.
     */
    public Order(String name, Integer volume, Double pricePerItem, OrderType orderType, ItemInfo itemInfo) {
        super(name, orderType, OrderStatus.SET, volume, pricePerItem, itemInfo);

        startTracking();
    }

    private void startTracking() {
        handleOutbidStatusChange();
        subscribe();
        scheduleHealthCheck();
        updateMarketPrice();
    }

    @EventHandler
    private void onDataUpdate(BazaarDataUpdateEvent e) {
        updateMarketPrice();
        handleOutbidStatusChange();
    }

    @EventHandler
    private void onUserOrderChange(UserOrdersChangeEvent e) {
        if (e.getChangeType() == UserOrdersChangeEvent.ChangeTypes.REMOVE || e.getOrder() != this) {
            return;
        }

        updateMarketPrice();
        handleOutbidStatusChange();
    }

    private void handleOutbidStatusChange() {
        Optional<PricingPosition> outbidOptional = findPricingPosition();

        if (outbidOptional.isEmpty()) {
            return;
        }

        PricingPosition newPosition = outbidOptional.get();

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
                openBazaar();
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
     * Opens the Bazaar order management screen after a short countdown if the player is not already there.
     */
    public void openBazaar() {
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();

        if (screenInfo.inBazaar()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            for (int i = 3; i >= 1; i--) {
                try {
                    if (i == 3) {
                        PlayerActionUtil.notifyAll("Opening bazaar in 3");
                    } else {
                        PlayerActionUtil.notifyAll(String.valueOf(i));
                    }

                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }

            PlayerActionUtil.runCommand("managebazaarorders");
        });
    }


    /**
     * @return index of this order within the persisted user order list.
     */
    public int getIndex() {
        return UserOrdersStorage.INSTANCE.get().indexOf(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());

        sb.append(name).append("[").append(getIndex()).append("]");

        if (amountClaimed != 0) {
            sb.append(", amount claimed: ").append(amountClaimed);
        }

        sb.append(", type: ").append(getOrderType().getString());

        if (status == OrderStatus.FILLED) {
            sb.append(", status: ").append(status);
        }

        sb.append(")");

        return sb.toString();
    }

    /**
     * Converts a filled order into its opposite side for flipping and resets fill tracking.
     *
     * @param newPrice price to use for the flipped order
     */
    public void flipItem(double newPrice) {
        flipPrices(newPrice);
        updateMarketPrice();

        this.amountFilled = 0;
        this.status = OrderStatus.SET;

        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.UPDATE, this));
    }

    public double getMarketPrice(OrderType orderType) {
        updateMarketPrice();

        return this.getPriceForPosition(PricingPosition.MATCHED, orderType);
    }

    /**
     * Calculates a competitive flip price using the opposite side of the market.
     *
     * @return price .1 coin more competitive than market rate.
     */
    public double getUndercutPrice(OrderType orderType) {
        updateMarketPrice();

        return this.getPriceForPosition(PricingPosition.COMPETITIVE, orderType);
    }

    /**
     * Calculates a non-competitive flip price.
     *
     * @return price .1 coin less competitive than market rate.
     */
    public double getOutbidPrice(OrderType orderType) {
        updateMarketPrice();

        return this.getPriceForPosition(PricingPosition.OUTBID, orderType);
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
     * Removes this order from the tracked watched items list and notifies listeners.
     */
    public void removeFromWatchedItems() {
        if (!UserOrdersStorage.INSTANCE.get().remove(this)) {
            PlayerActionUtil.notifyAll("Error removing " + name + " from watched items. Item couldn't be found.");
        }

        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.REMOVE, this));

        UserOrdersStorage.INSTANCE.save();
    }
}
