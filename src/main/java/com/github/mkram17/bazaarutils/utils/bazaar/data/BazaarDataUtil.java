package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.PriceType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class BazaarDataUtil {
    /**
     * Get the number of orders at an exact price for a product & price type.
     * @return OptionalInt empty if reply / product / priceType invalid or not found.
     */
    public static OptionalInt getOrderCountOptional(String productId, OrderType orderType, double price) {
        CustomBazaarReply reply = BazaarDataManager.getCurrentReply();

        PriceType priceType = orderType.asPriceType();

        if (reply == null || productId == null || priceType == null) {
            return OptionalInt.empty();
        }

        try {
            ProductData product = reply.getProduct(productId);

            if (product == null) {
                return OptionalInt.empty();
            }

            List<ProductSummary> list = switch (priceType) {
                case INSTABUY -> product.getBuySummary();
                case INSTASELL -> product.getSellSummary();
            };

            if (list == null) {
                return OptionalInt.empty();
            }

            for (ProductSummary summary : list) {
                if (Double.compare(summary.getPricePerUnit(), price) == 0) {
                    return OptionalInt.of((int) summary.getOrders());
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
        CustomBazaarReply reply = BazaarDataManager.getCurrentReply();

        PriceType priceType = orderType.asPriceType();

        if (reply == null || productId == null || priceType == null) {
            return OptionalDouble.empty(); //TODO maybe throw error here instead. Needs testing to make sure it doesn't happen too frequently or at times where it is expected behavior
        }

        try {
            ProductData product = reply.getProduct(productId);

            if (product == null) {
                return OptionalDouble.empty();
            }

            return switch (priceType) {
                case INSTABUY -> {
                    List<ProductSummary> buySummary = product.getBuySummary();

                    if (buySummary == null || buySummary.isEmpty()) {
                        yield OptionalDouble.of(0.0);
                    }

                    yield OptionalDouble.of(buySummary.getFirst().getPricePerUnit());
                }
                case INSTASELL -> {
                    List<ProductSummary> sellSummary = product.getSellSummary();

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

    /**
     * Checks whether the provided string is a known bazaar product ID.
     * Uses in-memory data only (current reply + conversion cache).
     */
    public static boolean isValidProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            return false;
        }

        CustomBazaarReply reply = BazaarDataManager.getCurrentReply();
        if (reply != null && reply.getProduct(productId) != null) {
            return true;
        }

        BazaarDataManager.ensureConversionsLoaded();
        return BazaarDataManager.getNameToProductIdCache().containsValue(productId);
    }

    public static Optional<String> findProductIdOptional(String naturalName) {
        if (naturalName == null || naturalName.isBlank()) {
            return Optional.empty();
        }

        BazaarDataManager.ensureConversionsLoaded();

        return Optional.ofNullable(BazaarDataManager.getNameToProductIdCache().get(naturalName.toLowerCase(Locale.ROOT)));
    }
}
