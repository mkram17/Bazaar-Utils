package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public final class OrderUtil {
    public static List<Order> getUserOrders() {
        return UserOrdersStorage.INSTANCE.get();
    }

    public static Optional<Order> getUserOrderFromIndex(int slotIndex) {
        return getUserOrders().stream()
                .filter(order ->
                        order.getItemInfo() != null
                        && order.getItemInfo().slotIndex().equals(slotIndex))
                .findFirst();
    }

    /**
     * Opens the Bazaar order management screen after a short countdown if the player is not already there.
     */
    public static void openBazaar() {
        if (ScreenManager.getInstance().isCurrent(BazaarScreens.ALL.toArray(ScreenType[]::new))) {
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

    public static void trackUserOrder(Order item) {
        if (item == null) return;
        assert item.getProductID() != null;
        UserOrdersStorage.INSTANCE.get().add(item);
        PlayerActionUtil.notifyAll("Added item: § " + item, NotificationType.ORDERDATA);
        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.ADD, item));
        UserOrdersStorage.INSTANCE.save();
    }

    public static double getPriceForPosition(String productID, PricingPosition pricingPosition, OrderType orderType) {
        if (productID == null || pricingPosition == null || orderType == null) {
            return 0;
        }

        double marketSellPrice = resolvePrice(productID, OrderType.SELL);
        double marketBuyPrice = resolvePrice(productID, OrderType.BUY);

        return switch (orderType) {
            case SELL -> switch (pricingPosition) {
                case COMPETITIVE -> marketSellPrice - 0.1;
                case MATCHED -> marketSellPrice;
                case OUTBID -> marketSellPrice + 0.1;
            };
            case BUY -> switch (pricingPosition) {
                case COMPETITIVE -> marketBuyPrice + 0.1;
                case MATCHED -> marketBuyPrice;
                case OUTBID -> marketBuyPrice - 0.1;
            };
        };
    }

    private static double resolvePrice(String productID, OrderType orderType) {
        OptionalDouble priceOptional = BazaarDataManager.findItemPriceOptional(productID, orderType);
        if (priceOptional.isEmpty()) {
            return -1;
        }
        return Util.truncateNum(priceOptional.getAsDouble());
    }
}
