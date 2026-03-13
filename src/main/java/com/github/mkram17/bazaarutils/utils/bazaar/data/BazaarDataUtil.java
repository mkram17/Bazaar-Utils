package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.PriceType;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.*;

public class BazaarDataUtil {
    /**
     * Get the number of orders at an exact price for a product & price type.
     * @return OptionalInt empty if reply / product / priceType invalid or not found.
     */
    public static OptionalInt getOrderCountOptional(String productId, OrderType orderType, double price) {
        SkyBlockBazaarReply reply = BazaarDataManager.getCurrentReply();

        PriceType priceType = orderType.asPriceType();

        if (reply == null || productId == null || priceType == null) {
            return OptionalInt.empty();
        }

        try {
            SkyBlockBazaarReply.Product product = reply.getProduct(productId);

            if (product == null) {
                return OptionalInt.empty();
            }

            List<SkyBlockBazaarReply.Product.Summary> list = switch (priceType) {
                case INSTABUY -> product.getBuySummary();
                case INSTASELL -> product.getSellSummary();
            };

            if (list == null) {
                return OptionalInt.empty();
            }

            for (SkyBlockBazaarReply.Product.Summary s : list) {
                if (Double.compare(s.getPricePerUnit(), price) == 0) {
                    return OptionalInt.of((int) s.getOrders());
                }
            }

            return OptionalInt.of(0);
        } catch (Exception e) {
            Util.notifyError("Error in getOrderCountOptional for productId=" + productId, e);

            return OptionalInt.empty();
        }
    }

    /**
     * Empty can mean: reply/product/priceType invalid or not found; exception while finding price
     * BUY (top of buySummary aka people's sell orders). SELL (top of sellSummary, aka people's buy orders).
     * @return OptionalDouble price found.
     */
    public static OptionalDouble findItemPriceOptional(String productId, OrderType orderType) {
        SkyBlockBazaarReply reply = BazaarDataManager.getCurrentReply();

        PriceType priceType = orderType.asPriceType();

        if (reply == null || productId == null || priceType == null) {
            return OptionalDouble.empty(); //TODO maybe throw error here instead. Needs testing to make sure it doesn't happen too frequently or at times where it is expected behavior
        }

        try {
            SkyBlockBazaarReply.Product product = reply.getProduct(productId);

            if (product == null) {
                return OptionalDouble.empty();
            }

            return switch (priceType) {
                case INSTABUY -> {
                    List<SkyBlockBazaarReply.Product.Summary> buySummary = product.getBuySummary();

                    if (buySummary == null || buySummary.isEmpty()) {
                        yield OptionalDouble.of(0.0);
                    }

                    yield OptionalDouble.of(buySummary.getFirst().getPricePerUnit());
                }
                case INSTASELL -> {
                    List<SkyBlockBazaarReply.Product.Summary> sellSummary = product.getSellSummary();

                    if (sellSummary == null || sellSummary.isEmpty()) {
                        yield OptionalDouble.of(0.0);
                    }

                    yield OptionalDouble.of(sellSummary.getFirst().getPricePerUnit());
                }
            };
        } catch (Exception e) {
            Util.notifyError("Error in findItemPriceOptional for productId=" + productId, e);

            return OptionalDouble.empty();
        }
    }

    public static Optional<String> findProductIdOptional(String naturalName) {
        if (naturalName == null || naturalName.isBlank()) {
            return Optional.empty();
        }

        BazaarDataManager.ensureConversionsLoaded();

        return Optional.ofNullable(BazaarDataManager.getNameToProductIdCache().get(naturalName.toLowerCase(Locale.ROOT)));
    }
}
