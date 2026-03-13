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
        return productBuyOrders;
    }

    public List<ProductOrder> getSellOrders() {
        return productSellOrders;
    }

    public void insertInstaSellSummary(ProductOrder productOrder) {
        int index = 0;
        for (ProductOrder existing : productBuyOrders) {
            if (productOrder.getPricePerUnit() < existing.getPricePerUnit()) {
                break;
            }
            index++;
        }
        productBuyOrders.add(index, productOrder);
    }

    public void insertInstaBuySummary(ProductOrder productOrder) {
        int index = 0;
        for (ProductOrder existing : productSellOrders) {
            if (productOrder.getPricePerUnit() > existing.getPricePerUnit()) {
                break;
            }
            index++;
        }
        productSellOrders.add(index, productOrder);
    }

    public List<UserProductOrder> getUserInstaSellSummaries() {
        return productBuyOrders.stream()
                .filter(UserProductOrder.class::isInstance)
                .map(UserProductOrder.class::cast)
                .toList();
    }

    public List<UserProductOrder> getUserInstaBuySummaries() {
        return productSellOrders.stream()
                .filter(UserProductOrder.class::isInstance)
                .map(UserProductOrder.class::cast)
                .toList();
    }
}
