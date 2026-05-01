package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceLevel;
import com.github.mkram17.bazaarutils.utils.bazaar.data.ProductData;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
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
        List<PriceLevel> sell = new ArrayList<>();
        List<PriceLevel> buy = new ArrayList<>();

        if (apiProduct.getSellSummary() != null) {
            var convertedSellSummaries = convertAPIProductSummaries(apiProduct.getSellSummary());
            sell.addAll(convertedSellSummaries);
        }

        if (apiProduct.getBuySummary() != null) {
            var convertedBuySummaries = convertAPIProductSummaries(apiProduct.getBuySummary());
            buy.addAll(convertedBuySummaries);
        }

        return new ProductData(productId, sell, buy);
    }

    public static List<PriceLevel> convertAPIProductSummaries(List<SkyBlockBazaarReply.Product.Summary> apiSummaries) {
        if (apiSummaries == null || apiSummaries.isEmpty()) {
            return List.of();
        }
        return apiSummaries.stream()
                .map(APIConversionUtil::fromAPIProductSummary)
                .toList();
    }

    public static PriceLevel fromAPIProductSummary(SkyBlockBazaarReply.Product.Summary apiSummary) {
        long now = System.currentTimeMillis();

        return new PriceLevel(
                apiSummary.getPricePerUnit(),
                apiSummary.getAmount(),
                (int) apiSummary.getOrders(),
                new DataOrigin.ApiSnapshot(now)
        );
    }
}
