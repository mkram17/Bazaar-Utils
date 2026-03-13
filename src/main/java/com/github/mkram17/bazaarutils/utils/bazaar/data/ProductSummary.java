package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.PriceType;
import lombok.Getter;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

public class ProductSummary {
    @Getter
    private final PriceType priceType;
    @Getter
    private final double pricePerUnit;
    @Getter
    private final long amount;
    @Getter
    private final long orders;

    public ProductSummary(PriceType priceType, double pricePerUnit, long amount, long orders) {
        this.priceType = priceType;
        this.pricePerUnit = pricePerUnit;
        this.amount = amount;
        this.orders = orders;
    }

    public static ProductSummary fromAPIProductSummary(SkyBlockBazaarReply.Product.Summary apiSummary, PriceType priceType) {
        return new ProductSummary(
                priceType,
                apiSummary.getPricePerUnit(),
                apiSummary.getAmount(),
                apiSummary.getOrders()
        );
    }

    public boolean hasOrders() {
        return orders >0 && pricePerUnit >0;
    }
}
