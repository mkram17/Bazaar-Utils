package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductData {
    @Getter
    private final String productId;
    private final List<ProductOrder> productBuyOrders;
    private final List<ProductOrder> productSellOrders;

    public ProductData(String productId, List<ProductOrder> productBuyOrders, List<ProductOrder> productSellOrders) {
        this.productId = productId;
        this.productBuyOrders = new ArrayList<>(productBuyOrders);
        this.productSellOrders = new ArrayList<>(productSellOrders);
    }

    public List<ProductOrder> getBuyOrders() {
        return Collections.unmodifiableList(productBuyOrders);
    }

    public List<ProductOrder> getSellOrders() {
        return Collections.unmodifiableList(productSellOrders);
    }
}
