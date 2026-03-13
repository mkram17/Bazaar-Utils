package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import lombok.Getter;
import net.hypixel.api.reply.AbstractReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.Map;
import java.util.stream.Collectors;

public class CustomBazaarReply extends AbstractReply {
    @Getter
    private final long lastUpdated;
    @Getter
    private final Map<String, ProductData> products;

    public CustomBazaarReply(long lastUpdated, Map<String, ProductData> products) {
        this.lastUpdated = lastUpdated;
        this.products = products;
    }

    public static CustomBazaarReply fromSkyBlockReply(SkyBlockBazaarReply reply) {
        AccessorSkyBlockBazaarReply accessor = (AccessorSkyBlockBazaarReply) reply;

        Map<String, SkyBlockBazaarReply.Product> sourceProducts = reply.getProducts();

        Map<String, ProductData> converted = (sourceProducts == null || sourceProducts.isEmpty())
                ? Map.of()
                : sourceProducts.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> ProductData.fromAPIProduct(e.getKey(), e.getValue())
                ));

        return new CustomBazaarReply(accessor.getLastUpdated(), converted);
    }

    public ProductData getProduct(String productId) {
        return products.get(productId);
    }
}
