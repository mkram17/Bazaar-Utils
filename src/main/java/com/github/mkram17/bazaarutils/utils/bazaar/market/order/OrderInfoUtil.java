package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;

import java.util.List;
import java.util.Optional;

public class OrderInfoUtil {
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
}
