package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class APIConversionUtil {
    
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
            var convertedSellSummaries = convertAPIProductSummaries(apiProduct.getSellSummary(), TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.INSTANT));
            sell.addAll(convertedSellSummaries);
        }

        if (apiProduct.getBuySummary() != null) {
            var convertedBuySummaries = convertAPIProductSummaries(apiProduct.getBuySummary(), TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.INSTANT));
            buy.addAll(convertedBuySummaries);
        }

        return new ProductData(productId, sell, buy);
    }

    public static List<ProductOrder> convertAPIProductSummaries(List<SkyBlockBazaarReply.Product.Summary> apiSummaries, TransactionType transactionType) {
        if (apiSummaries == null || apiSummaries.isEmpty()) {
            return List.of();
        }
        return apiSummaries.stream()
                .map(s -> fromAPIProductSummary(s, transactionType))
                .toList();
    }

    public static ProductOrder fromAPIProductSummary(SkyBlockBazaarReply.Product.Summary apiSummary, TransactionType transactionType) {
        return new ProductOrder(
                transactionType.getPriceType(),
                apiSummary.getPricePerUnit(),
                apiSummary.getAmount(),
                apiSummary.getOrders()
        );
    }
}
