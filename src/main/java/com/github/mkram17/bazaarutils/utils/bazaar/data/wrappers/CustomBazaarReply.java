package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import lombok.Getter;
import net.hypixel.api.reply.AbstractReply;

import java.util.List;
import java.util.Map;

public class CustomBazaarReply extends AbstractReply {
    @Getter
    private final long lastUpdated;
    @Getter
    private final Map<String, ProductData> products;

    public CustomBazaarReply(long lastUpdated, Map<String, ProductData> products) {
        this.lastUpdated = lastUpdated;
        this.products = products;
    }

    public ProductData getProduct(String productId) {
        return products.get(productId);
    }

    public void replaceUserProductOrders() {
        List<Order> userOrders = UserOrdersStorage.INSTANCE.get();

        for (Order order : userOrders) {
            ProductData product = getProduct(order.getProductID());
            APIConversionUtil.replaceUserOrdersInList(order, product.getSellOrders());
            APIConversionUtil.replaceUserOrdersInList(order, product.getBuyOrders());
        }
    }
}
