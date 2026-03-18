package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Stores Bazaar item information while automatically tracking market price updates and performing
 * health checks on product identifiers. Intended for order-like data that does not need the full
 * {@link Order} lifecycle.
 */
@ToString(callSuper=true)
public class OrderInfo extends PriceInfo {
    private static final double DEFAULT_TOLERANCE = 0.9;
    private static final double TOTAL_PRICE_ROUNDING_THRESHOLD = 10000;

    @Getter
    protected String productID; //Hypixel's code for the product
    @Getter @ConfigEntry(id = "name")
    protected final String name; //name of the item in game

    @Getter
    protected OrderStatus status;

    @Getter
    protected final Integer volume;

    @Getter @Setter
    protected double tolerance; //When finding item price, it can round to the nearest coin sometimes, so tolerance is needed for price calculations

    @Getter @Setter
    private ItemInfo itemInfo;

    /**
     * Creates a container that tracks market data for a specific Bazaar product.
     *
     * @param name         display name of the item
     * @param side whether this is a buy or sell transaction
     * @param status       status of the order
     * @param volume       quantity of the order
     * @param pricePerItem current price per unit for the order
     * @param itemInfo     optional UI context from the Bazaar screen
     */
    public OrderInfo(@Nullable String name, @Nullable TransactionType2.Side side, @Nullable OrderStatus status, @Nullable Integer volume, @Nullable Double pricePerItem, @Nullable ItemInfo itemInfo) {
        super(pricePerItem, TransactionType2.of(side, TransactionType2.Method.ORDER));

        this.name = name;
        this.itemInfo = itemInfo;
        this.status = status;
        this.volume = volume;
        this.tolerance = calculateTolerance();

        BazaarDataManager.findProductIdOptional(name).ifPresent(productId -> this.productID = productId);
        validateProductId(productID);
        findPricingPosition().ifPresent(pricingPosition -> this.pricingPosition = pricingPosition);
    }

    //TODO validate name/product id with method specifically for that. Maybe can switch findProdIdOpt for non optional version and then rely on validation method.
    private void validateProductId(String productId) {
        if(productId == null || productId.isBlank()) {
            Util.notifyError("Error setting product id for " + this, new Throwable("Product ID cannot be null or blank"));
        }
    }

    private double calculateTolerance() {
        //default tolerance
        if (this.pricePerItem == null || this.volume == null) {
            return DEFAULT_TOLERANCE;
        }
        //doesn't round prices when the total is over 10k
        if (this.pricePerItem * this.volume < TOTAL_PRICE_ROUNDING_THRESHOLD) {
            return 0;
        } else {
            double priceMaximumInaccuracy = DEFAULT_TOLERANCE / volume; //0.9 coins is the most that it can be off per unit and not show in places where it rounds

            return (Math.round(priceMaximumInaccuracy * 10)) / 10.0;
        }
    }

    /**
     * Checks whether a provided item name can be resolved to a Bazaar product.
     *
     * @param itemName name to validate
     * @return {@code true} when a product ID exists for the name
     */
    public static boolean isValidName(String itemName) {
        return itemName != null && BazaarDataManager.findProductIdOptional(itemName).isPresent();
    }

    /**
     * Determines whether the order price is competitive, matched, or outbid relative to the market.
     *
     * @return status reflecting how this order compares to current prices, if calculable
     */
    public Optional<PricingPosition> findPricingPosition() {
        if (this.pricePerItem == null) {
            return Optional.empty();
        }

        double marketPrice = OrderUtil.getPriceForPosition(productID, PricingPosition.MATCHED, getTransactionType());

        var orderCountOpt = BazaarDataManager.getOrderCountOptional(productID, getTransactionType(), getPricePerItem());

        if (orderCountOpt.isEmpty()) {
            return Optional.empty();
        }

        int orderCount = orderCountOpt.getAsInt();

        if (transactionType != null && transactionType.getSide() == TransactionType2.Side.BUY) {
            if (this.pricePerItem > marketPrice) {
                return Optional.of(PricingPosition.COMPETITIVE);
            } else if (this.pricePerItem < marketPrice) {
                return Optional.of(PricingPosition.OUTBID);
            } else {
                if (orderCount > 1) {
                    return Optional.of(PricingPosition.MATCHED);
                }
            }
        } else {
            if (pricePerItem < marketPrice) {
                return Optional.of(PricingPosition.COMPETITIVE);
            } else if (pricePerItem > marketPrice) {
                return Optional.of(PricingPosition.OUTBID);
            } else {
                if (orderCount > 1) {
                    return Optional.of(PricingPosition.MATCHED);
                }
            }
        }

        return Optional.of(PricingPosition.COMPETITIVE);
    }

    /**
     * Tests whether this order corresponds to another order, optionally using loose comparisons for volume and price.
     *
     * @param other    order to compare against
     * @param isStrict when true requires exact matches, when false allows small deviations
     * @return {@code true} if the two orders can be considered the same
     */
    public boolean isSimilarTo(Order other, boolean isStrict) {
        String otherOrderName = other.getName();
        Double otherOrderPrice = other.getPricePerItem();
        Integer otherOrderVolume = other.getVolume();
        int otherOrderAmountUnclaimed = other.getAmountFilled() - other.getAmountClaimed();
        TransactionType2 transactionType = other.getTransactionType();

        if (isStrict) {
            return isStrictlySimilarTo(otherOrderName, otherOrderPrice, otherOrderVolume, transactionType);
        }

        return isLooselySimilarTo(otherOrderName, otherOrderPrice, otherOrderVolume, otherOrderAmountUnclaimed, transactionType);
    }

    private boolean isStrictlySimilarTo(String otherOrderName, Double otherOrderPrice, Integer otherOrderVolume, TransactionType2 transactionType) {
        return (areAnyNull(this.pricePerItem, otherOrderPrice) || isSimilarPrice(otherOrderPrice)) &&
                (areAnyNull(this.volume, otherOrderVolume) || this.volume.equals(otherOrderVolume)) &&
                (areAnyNull(this.name, otherOrderName) || this.name.equalsIgnoreCase(otherOrderName)) &&
                (areAnyNull(this.transactionType, transactionType) || this.transactionType.getSide() == transactionType.getSide());
    }

    private boolean isLooselySimilarTo(String otherOrderName, Double otherOrderPrice, Integer otherOrderVolume, int otherOrderAmountUnclaimed, TransactionType2 transactionType) {
        return (areAnyNull(this.pricePerItem, otherOrderPrice) || this.isSimilarPrice(otherOrderPrice)) &&
                (areAnyNull(this.volume, otherOrderVolume) || Util.genericIsSimilarValue(this.getVolume(), otherOrderVolume, 0.05 * otherOrderVolume) || this.getVolume().equals(otherOrderAmountUnclaimed)) && // sometimes the only volume that can be found is the amount that is unclaimed, like in FlipHelper
                (areAnyNull(this.name, otherOrderName) || this.getName().equalsIgnoreCase(otherOrderName)) &&
                (areAnyNull(this.transactionType, transactionType) || this.getTransactionType().getSide() == transactionType.getSide());
    }

    private boolean areAnyNull(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Finds a matching order in the provided list, preferring the closest match when multiple entries are similar.
     *
     * @param list list of existing orders to search
     * @return best matching order if one exists
     */
    public Optional<Order> findOrderInList(List<Order> list) {
        List<Order> itemList = findAllMatchesInList(list);

        if (itemList.size() > 1) {
            return Optional.of(findBestMatch(itemList));
        }

        if (itemList.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(itemList.getFirst());
    }

    /**
     * Locates all orders in the provided list that resemble this order.
     *
     * @param list candidate orders
     * @return list of matches, ordered first by strict then loose similarity
     */
    public List<Order> findAllMatchesInList(List<Order> list) {
        List<Order> itemList = new ArrayList<>();

        for (Order item : list) {
            if (this.isSimilarTo(item, true)) {
                itemList.add(item);
            }
        }

        if (itemList.isEmpty()) {
            for (Order item : list) {
                if (this.isSimilarTo(item, false)) {
                    itemList.add(item);
                }
            }
        }

        return itemList;
    }
    //TODO some error with maximum rounding or finding the price. either finding price can round down by .1 accidentally or maximum rounding calculation is wrong
    private boolean isSimilarPrice(double price) {
        //tolerance + 1% of price to account for rounding errors (1% is just in case, but shouldnt matter)
        return Util.genericIsSimilarValue(pricePerItem, price, tolerance + price * .01);
    }

    /**
     * Projects each stored user order to a single variable, such as volume or price. For example,
     * {@code getVariables(BazaarOrder::getPricePerItem)} extracts all prices from user orders in
     * {@link UserOrdersStorage}.
     *
     * @param <T>      type of value extracted from each order
     * @param variable accessor used to extract a value from each order
     * @return immutable list of extracted values
     */
    public static <T> List<T> getVariables(Function<Order, T> variable) {
        return UserOrdersStorage.INSTANCE.get()
                .stream()
                .map(variable)
                .toList();
    }
    /** Used for when there are duplicate matches found and the best should be chosen to use.
     * Typically, volume is the variable that is different, but it can also be price
    */
    private Order findBestMatch(List<Order> list) {
        return list.stream()
                .min(getVolumeThenPriceComparator())
                .orElse(list.getFirst());
    }

    private Comparator<Order> getVolumeThenPriceComparator() {
        Comparator<Order> volumeComparator = Comparator.comparingDouble(order -> {
            if (areAnyNull(this.getVolume(), order.getVolume())) {
                return Double.MAX_VALUE;
            }

            return Math.abs(order.getVolume() - this.getVolume());
        });

        Comparator<Order> priceComparator = Comparator.comparingDouble(order -> {
            if (areAnyNull(this.pricePerItem, order.getPricePerItem())) {
                return Double.MAX_VALUE;
            }

            return Math.abs(order.getPricePerItem() - this.pricePerItem);
        });

        return volumeComparator.thenComparing(priceComparator);
    }

    /**
     * Converts the current container into a fully tracked {@link Order}.
     */
    public Order toBazaarOrder() {
        return new Order(name, volume, pricePerItem, transactionType.getSide(), null);
    }
}
