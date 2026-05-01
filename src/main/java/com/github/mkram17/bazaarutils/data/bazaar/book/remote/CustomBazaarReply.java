package com.github.mkram17.bazaarutils.data.bazaar.book.remote;

import com.github.mkram17.bazaarutils.data.bazaar.book.ProductOrder;
import lombok.Getter;
import net.hypixel.api.reply.AbstractReply;

import java.util.Map;

public class CustomBazaarReply extends AbstractReply {
    @Getter
    private final long lastUpdated;
    @Getter
    private final Map<String, ProductOrder> products;

    public CustomBazaarReply(long lastUpdated, Map<String, ProductOrder> products) {
        this.lastUpdated = lastUpdated;
        this.products = products;
    }

    public ProductOrder getProduct(String productId) {
        return products.get(productId);
    }
}
