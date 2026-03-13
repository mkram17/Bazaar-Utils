package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.PriceType;
import lombok.Getter;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductData {
    @Getter
    private final String productId;
    private final List<ProductSummary> sellSummary;
    private final List<ProductSummary> buySummary;

    public ProductData(String productId, List<ProductSummary> sellSummary, List<ProductSummary> buySummary) {
        this.productId = productId;
        this.sellSummary = new ArrayList<>(sellSummary);
        this.buySummary = new ArrayList<>(buySummary);
    }

    public static ProductData fromAPIProduct(String productId, SkyBlockBazaarReply.Product apiProduct) {
        List<ProductSummary> sell = new ArrayList<>();
        List<ProductSummary> buy = new ArrayList<>();

        if (apiProduct.getSellSummary() != null) {
            for (SkyBlockBazaarReply.Product.Summary s : apiProduct.getSellSummary()) {
                sell.add(ProductSummary.fromAPIProductSummary(s, PriceType.INSTASELL));
            }
        }

        if (apiProduct.getBuySummary() != null) {
            for (SkyBlockBazaarReply.Product.Summary s : apiProduct.getBuySummary()) {
                buy.add(ProductSummary.fromAPIProductSummary(s, PriceType.INSTABUY));
            }
        }

        return new ProductData(productId, sell, buy);
    }

    public List<ProductSummary> getSellSummary() {
        return Collections.unmodifiableList(sellSummary);
    }

    public List<ProductSummary> getBuySummary() {
        return Collections.unmodifiableList(buySummary);
    }

    public void insertInstaSellSummary(ProductSummary productSummary) {
        int index = 0;
        for (ProductSummary existing : sellSummary) {
            if (productSummary.getPricePerUnit() < existing.getPricePerUnit()) {
                break;
            }
            index++;
        }
        sellSummary.add(index, productSummary);
    }

    public void insertInstaBuySummary(ProductSummary productSummary) {
        int index = 0;
        for (ProductSummary existing : buySummary) {
            if (productSummary.getPricePerUnit() > existing.getPricePerUnit()) {
                break;
            }
            index++;
        }
        buySummary.add(index, productSummary);
    }

    public List<UserProductSummary> getUserInstaSellSummaries() {
        return sellSummary.stream()
                .filter(UserProductSummary.class::isInstance)
                .map(UserProductSummary.class::cast)
                .toList();
    }

    public List<UserProductSummary> getUserInstaBuySummaries() {
        return buySummary.stream()
                .filter(UserProductSummary.class::isInstance)
                .map(UserProductSummary.class::cast)
                .toList();
    }
}
