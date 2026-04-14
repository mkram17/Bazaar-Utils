package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers.CustomBazaarReply;
import com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers.ProductData;
import com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers.ProductOrder;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;

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
    public static OptionalInt getOrderCountOptional(String productId, TransactionType transactionType, double price) {
        CustomBazaarReply reply = BazaarDataManager.getCurrentReply();

        if (transactionType == null) {
            return OptionalInt.empty();
        }

        PriceType priceType = transactionType.getPriceType();

        if (reply == null || productId == null || priceType == null) {
            return OptionalInt.empty();
        }

        try {
            ProductData product = reply.getProduct(productId);

            if (product == null) {
                return OptionalInt.empty();
            }

            List<ProductOrder> list = switch (priceType) {
                case INSTABUY -> product.getBuyOrders();
                case INSTASELL -> product.getSellOrders();
            };

            if (list == null) {
                return OptionalInt.empty();
            }

            for (ProductOrder s : list) {
                if (Double.compare(s.pricePerUnit(), price) == 0) {
                    return OptionalInt.of((int) s.volume());
                }
            }

            return OptionalInt.of(0);
        } catch (Exception e) {
            Util.notifyError("Error in getOrderCountOptional for productID=" + productId, e);

            return OptionalInt.empty();
        }
    }

    /**
     * Find the top bazaar price for a product based on the given {@link TransactionType}.
     * The returned {@link OptionalDouble} is empty if the reply, product ID, or derived {@link PriceType}
     * is {@code null}, if the product cannot be found, or if an exception occurs while resolving the price.
     * If the selected summary list exists but is empty, this method returns {@code OptionalDouble.of(0.0)}.
     *
     * @param productId       the bazaar product ID to look up
     * @param transactionType the transaction type whose {@link PriceType} controls which summary is queried
     * @return an {@link OptionalDouble} containing the resolved price per unit, or empty if unavailable
     */
    public static OptionalDouble findItemPriceOptional(String productId, TransactionType transactionType) {
        CustomBazaarReply reply = BazaarDataManager.getCurrentReply();
        if (transactionType == null) {
            return OptionalDouble.empty();
        }

        PriceType priceType = transactionType.getPriceType();

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
                    List<ProductOrder> buySummary = product.getBuyOrders();

                    if (buySummary == null || buySummary.isEmpty()) {
                        yield OptionalDouble.of(0.0);
                    }

                    yield OptionalDouble.of(buySummary.getFirst().pricePerUnit());
                }
                case INSTASELL -> {
                    List<ProductOrder> sellSummary = product.getSellOrders();

                    if (sellSummary == null || sellSummary.isEmpty()) {
                        yield OptionalDouble.of(0.0);
                    }

                    yield OptionalDouble.of(sellSummary.getFirst().pricePerUnit());
                }
            };
        } catch (Exception e) {
            Util.notifyError("Error in findItemPriceOptional for productID=" + productId, e);

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

        ResourceManager.ensureConversionsLoaded();
        return ResourceManager.getNameToProductIdCache().containsValue(productId);
    }

    public static Optional<String> findProductIdOptional(String naturalName) {
        if (naturalName == null || naturalName.isBlank()) {
            return Optional.empty();
        }

        ResourceManager.ensureConversionsLoaded();

        return Optional.ofNullable(ResourceManager.getNameToProductIdCache().get(naturalName.toLowerCase(Locale.ROOT)));
    }
}
