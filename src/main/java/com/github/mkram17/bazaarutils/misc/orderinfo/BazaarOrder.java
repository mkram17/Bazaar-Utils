package com.github.mkram17.bazaarutils.misc.orderinfo;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.features.OutbidOrderHandler;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;


//TODO figure out how to handle rounding with price
//TODO sometimes a BazaarOrder is created that is for the same order as one in userOrders, but not the same object. This causes problems sometimes, and should be fixed.
//TODO use last viewed item in bazaar to help with finding accurate price instead of just chat message
/**
 * Extension of {@link OrderInfoContainer} that tracks live Bazaar orders and reacts to events such
 * as outbids, user order changes, and price updates.
 */
@Slf4j
public class BazaarOrder extends OrderInfoContainer {

    public static final int OUTBID_ORDER_NOTIFICATIONS = 3; // number of notifications to send when an order becomes outdated

    @Getter @Setter
    private Statuses fillStatus; //only used to determine if order is set or filled, not outdated, competitive, or matched
    @Getter @Setter
    private int amountClaimed = 0;
    @Getter
    private int amountFilled = 0;


    /**
     * Creates a Bazaar order with no captured {@link ItemInfo} context.
     */
    public BazaarOrder(String name, Integer volume, Double pricePerItem, PriceType priceType) {
        this(name, volume, pricePerItem, priceType, null);
    }

    /**
     * Creates a Bazaar order, initializing ItemInfo with slot index and ItemStack of the order.
     */
    public BazaarOrder(String name, Integer volume, Double pricePerItem, PriceType priceType, ItemInfo itemInfo) {
        super(name, volume, pricePerItem, priceType, itemInfo);
        this.fillStatus = Statuses.SET;
        startTracking();
    }

    private void startTracking(){
        handleOutbidStatusChange();
        subscribe();
        scheduleHealthCheck();
        updateMarketPrice();
    }

    @EventHandler
    private void onDataUpdate(BazaarDataUpdateEvent e){
        updateMarketPrice();
        handleOutbidStatusChange();
    }

    @EventHandler
    private void onUserOrderChange(UserOrdersChangeEvent e) {
        if(e.getChangeType() == UserOrdersChangeEvent.ChangeTypes.REMOVE || e.getOrder() != this)
            return;
        updateMarketPrice();
        handleOutbidStatusChange();
    }

    private void handleOutbidStatusChange(){
        Optional<Statuses> outbidOptional = findOutbidStatus();
        if(outbidOptional.isEmpty()) return;

        Statuses newStatus = outbidOptional.get();
        if(outbidStatus != newStatus){
            outbidStatus = newStatus;
            onOutbid(newStatus == Statuses.OUTBID);
        }
    }

    private void onOutbid(boolean isOutbid){
        boolean shouldNotifyUser = BUConfig.get().outbidOrderHandler.isNotifyOutbid();
        boolean shouldPlayNotificationSound = BUConfig.get().outbidOrderHandler.isNotificationSound();
        boolean shouldAutoOpenBazaar = BUConfig.get().outbidOrderHandler.isAutoOpenEnabled();

        if(!shouldNotifyUser || !BUConfig.get().userOrders.contains(this)) return;
        if(this.getFillStatus() == OrderInfoContainer.Statuses.FILLED) return;

        MutableComponent message;
        if (isOutbid) {
            message = OutbidOrderHandler.getOutbidMessage(this);
            if (BUConfig.get().developerMode) {
                message.append(Component.literal(". Market Price: " + this.getMarketPrice(this.getPriceType()) + " Order Price: " + this.getPricePerItem()));
            }
            if(shouldAutoOpenBazaar){
                openBazaar();
            }
            Minecraft client = Minecraft.getInstance();
            var player = client.player;
            if (shouldPlayNotificationSound && player != null) {
                SoundUtil.notifyMultipleTimes(OUTBID_ORDER_NOTIFICATIONS);
            }
            Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyChatCommand(message, "managebazaarorders"));
        } else if(this.getOutbidStatus() == OrderInfoContainer.Statuses.COMPETITIVE) {
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
        if(screenInfo.inBazaar() )
            return;
        CompletableFuture.runAsync(() ->{
            for(int i = 3; i >= 1; i--) {
                try {
                    if(i == 3)
                        PlayerActionUtil.notifyAll("Opening bazaar in 3");
                    else
                        PlayerActionUtil.notifyAll(String.valueOf(i));
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
        return BUConfig.get().userOrders.indexOf(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append(name).append("[").append(getIndex()).append("]");
        if (amountClaimed != 0) {
            sb.append(", amount claimed: ").append(amountClaimed);
        }
        sb.append(", type: ").append(getPriceType().getString());
        if (fillStatus == Statuses.FILLED) {
            sb.append(", status: ").append(fillStatus);
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
        this.fillStatus = Statuses.SET;
        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.UPDATE, this));
    }

    /**
     * Calculates a competitive flip price using the opposite side of the market.
     *
     * @return price .1 coin more competitive than market rate.
     */
    public double getFlipPrice() {
        updateMarketPrice();
        Double marketPrice = getMarketPrice(priceType);
        Double marketOppositePrice = getMarketPrice(priceType.getOpposite());
        if (marketPrice == 0) {
            return 0;
        }

        if (getPriceType() == PriceType.INSTABUY) {
            return (marketOppositePrice + .1);
        } else {
            return (marketOppositePrice - .1);
        }
    }

    /**
     * Calculates a non-competitive flip price.
     * 
     * @return price .1 coin less competitive than market rate.
     */
    public double getOutbiddedPrice() {
        updateMarketPrice();

        Double marketPrice = getMarketPrice(priceType);
        Double marketOppositePrice = getMarketPrice(priceType.getOpposite());

        if (getPriceType() == PriceType.INSTABUY) { 
          return marketPrice + .1;
        } else {
          return marketOppositePrice + .1;
        }
    }

    /**
     * Updates the tracked filled amount and automatically marks the order as filled when the volume is reached.
     */
    public void setAmountFilled(int amountFilled) {
        this.amountFilled = amountFilled;
        if (this.amountFilled >= volume) {
            setFilled();
        } else {
            this.fillStatus = Statuses.SET;
        }
    }

    /**
     * Marks the order as fully filled and syncs the filled amount with the expected volume.
     */
    public void setFilled() {
        amountFilled = volume;
        fillStatus = Statuses.FILLED;
    }

    /**
     * Removes this order from the tracked watched items list and notifies listeners.
     */
    public void removeFromWatchedItems() {
        if (!BUConfig.get().userOrders.remove(this)) {
            PlayerActionUtil.notifyAll("Error removing " + name + " from watched items. Item couldn't be found.");
        }
        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.REMOVE, this));
        Util.scheduleConfigSave();
    }
}
