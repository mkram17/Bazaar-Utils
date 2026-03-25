package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.utils.storage.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
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

    public static void trackUserOrder(Order order) {
        if (order == null){
            return;
        }
        UserOrdersStorage.INSTANCE.get().add(order);
        PlayerActionUtil.notifyAll("Added order: § " + order, NotificationType.ORDERDATA);
        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.ADD, order));
        UserOrdersStorage.INSTANCE.save();
    }

    public static double getPriceForPosition(String productID, PricingPosition pricingPosition, TransactionType transactionType) {
        if (productID == null || pricingPosition == null || transactionType == null) {
            Util.notifyError("Call to OrderUtil.getPriceForPosition contained a null param", new Exception("Price resolution error"));
            return -1;
        }

        OptionalDouble marketSellPriceOpt = BazaarDataUtil.findItemPriceOptional(productID, TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER));
        OptionalDouble marketBuyPriceOpt = BazaarDataUtil.findItemPriceOptional(productID, TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER));

        if(marketBuyPriceOpt.isEmpty() || marketSellPriceOpt.isEmpty()) {
            Util.notifyError("Could not resolve market prices for " + productID + " when calculating price for position. Buy price present: " + marketBuyPriceOpt.isPresent() + " Sell price present: " + marketSellPriceOpt.isPresent(), new Exception("Price resolution error"));
            return -1;
        }

        double marketBuyPrice = marketBuyPriceOpt.getAsDouble();
        double marketSellPrice = marketSellPriceOpt.getAsDouble();

        return switch (transactionType.getPriceType()) {
            case PriceType.INSTABUY -> switch (pricingPosition) {
                case COMPETITIVE -> marketSellPrice - 0.1;
                case MATCHED -> marketSellPrice;
                case OUTBID -> marketSellPrice + 0.1;
            };
            case PriceType.INSTASELL -> switch (pricingPosition) {
                case COMPETITIVE -> marketBuyPrice + 0.1;
                case MATCHED -> marketBuyPrice;
                case OUTBID -> marketBuyPrice - 0.1;
            };
        };
    }
}
