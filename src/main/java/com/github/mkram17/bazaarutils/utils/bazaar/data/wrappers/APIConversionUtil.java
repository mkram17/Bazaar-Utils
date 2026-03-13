package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.PriceType;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class APIConversionUtil {

    public static List<UserProductOrder> replaceUserOrdersInList(Order order, List<ProductOrder> orders) {
        List<UserProductOrder> replaced = new ArrayList<>();

        for (int i = 0; i < orders.size(); i++) {
            ProductOrder productOrder = orders.get(i);
            if (!productOrder.equalsOrder(order)) {
                continue;
            }

            UserProductOrder replacement = new UserProductOrder(order, productOrder);
            orders.set(i, replacement);
            replaced.add(replacement);
        }

        return replaced;
    }
    
    public static CustomBazaarReply fromSkyBlockReply(SkyBlockBazaarReply reply) {
        AccessorSkyBlockBazaarReply accessor = (AccessorSkyBlockBazaarReply) reply;

        Map<String, SkyBlockBazaarReply.Product> sourceProducts = reply.getProducts();
        Map<String, ProductData> converted = convertAPIProducts(sourceProducts);

        return new CustomBazaarReply(accessor.getLastUpdated(), converted);
    }

    public static Map<String, ProductData> convertAPIProducts(Map<String, SkyBlockBazaarReply.Product> apiProducts) {
        if (apiProducts == null || apiProducts.isEmpty()) {
            return Map.of();
        }
        return apiProducts.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> fromAPIProduct(e.getKey(), e.getValue())
                ));
    }

    public static ProductData fromAPIProduct(String productId, SkyBlockBazaarReply.Product apiProduct) {
        List<ProductOrder> sell = new ArrayList<>();
        List<ProductOrder> buy = new ArrayList<>();

        if (apiProduct.getSellSummary() != null) {
            var convertedSellSummaries = convertAPIProductSummaries(apiProduct.getSellSummary(), PriceType.INSTASELL);
            sell.addAll(convertedSellSummaries);
        }

        if (apiProduct.getBuySummary() != null) {
            var convertedBuySummaries = convertAPIProductSummaries(apiProduct.getBuySummary(), PriceType.INSTABUY);
            buy.addAll(convertedBuySummaries);
        }

        return new ProductData(productId, sell, buy);
    }

    public static List<ProductOrder> convertAPIProductSummaries(List<SkyBlockBazaarReply.Product.Summary> apiSummaries, PriceType priceType) {
        if (apiSummaries == null || apiSummaries.isEmpty()) {
            return List.of();
        }
        return apiSummaries.stream()
                .map(s -> fromAPIProductSummary(s, priceType))
                .toList();
    }

    public static ProductOrder fromAPIProductSummary(SkyBlockBazaarReply.Product.Summary apiSummary, PriceType priceType) {
        return new ProductOrder(
                priceType,
                apiSummary.getPricePerUnit(),
                apiSummary.getAmount(),
                apiSummary.getOrders()
        );
    }
}
