package com.github.mkram17.bazaarutils.utils.bazaar.data;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductData {
    @Getter
    private final String productId;
    private final List<PriceLevel> productBuyOrders;
    private final List<PriceLevel> productSellOrders;

    public ProductData(String productId, List<PriceLevel> productBuyOrders, List<PriceLevel> productSellOrders) {
        this.productId = productId;
        this.productBuyOrders = new ArrayList<>(productBuyOrders);
        this.productSellOrders = new ArrayList<>(productSellOrders);
    }

    public List<PriceLevel> getBuyOrders() {
        return Collections.unmodifiableList(productBuyOrders);
    }

    public List<PriceLevel> getSellOrders() {
        return Collections.unmodifiableList(productSellOrders);
    }
}
