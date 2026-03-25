package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.utils.storage.UserOrdersStorage;
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
}
