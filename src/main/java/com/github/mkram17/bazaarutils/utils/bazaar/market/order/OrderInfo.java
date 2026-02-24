package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.listener.AbstractListener;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.ItemInfo;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Stores Bazaar item information while automatically tracking market price updates and performing
 * health checks on product identifiers. Intended for order-like data that does not need the full
 * {@link Order} lifecycle.
 */
//TODO turn into builder class
@ConfigObject
public class OrderInfo extends PriceInfo implements AbstractListener {
    private static final double DEFAULT_TOLERANCE = 0.9;
    private static final double TOTAL_PRICE_ROUNDING_THRESHOLD = 10000;

    @Getter @ConfigEntry(id = "productID")
    protected String productID; //Hypixel's code for the product
    @Getter @ConfigEntry(id = "name")
    protected final String name; //name of the item in game

    @Getter @ConfigEntry(id = "status")
    protected OrderStatus status;

    @Getter @ConfigEntry(id = "volume")
    protected final Integer volume;

    @Getter @Setter
    protected double tolerance; //When finding item price, it can round to the nearest coin sometimes, so tolerance is needed for price calculations

    @Getter @Setter @ConfigEntry(id = "itemInfo") //TODO fix config serialization for this
    private ItemInfo itemInfo;

    /**
     * Creates a container that tracks market data for a specific Bazaar product.
     *
     * @param name         display name of the item
     * @param orderType    whether the price represents a sell (buy order) or buy (sell order)
     * @param status       status of the order
     * @param volume       quantity of the order
     * @param pricePerItem current price per unit for the order
     * @param itemInfo     optional UI context from the Bazaar screen
     */
    public OrderInfo(@Nullable String name, @Nullable OrderType orderType, @Nullable OrderStatus status, @Nullable Integer volume, @Nullable Double pricePerItem, @Nullable ItemInfo itemInfo) {
        super(pricePerItem, orderType);

        this.name = name;
        this.itemInfo = itemInfo;
        this.status = status;
        this.volume = volume;
        this.tolerance = calculateTolerance();

        validateProduct();
        BazaarDataManager.findProductIdOptional(name).ifPresent(productId -> this.productID = productId);
        findPricingPosition().ifPresent(pricingPosition -> this.pricingPosition = pricingPosition);
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
     * Refreshes cached market price data for this product.
     */
    public void updateMarketPrice() {
        updateMarketPrice(productID);
    }

    private void validateProduct() {
        if (this.productID == null && this.name != null) {
            if (!fixProductID()) {
                Util.notifyError("Product ID for " + this.name + " is null. This may cause issues", new Throwable());
            }
        }
    }

    protected void scheduleHealthCheck() {
        long START_DELAY_SECONDS = 60;
        long CHECK_INTERVAL_SECONDS = 30;

        BazaarUtils.BUExecutorService.scheduleAtFixedRate(() -> {
            if (!fixProductID()) {
                Util.logError("Could not fix product ID for " + this.name + ". This may cause the mod to work improperly.", new Throwable());
            }
        }, START_DELAY_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }


    //returns true if productID is safe/fixed after run, and false if it is not
    private boolean fixProductID() {
        if (isProductIDHealthy()) {
            return true;
        }

        Optional<String> newProductID = BazaarDataManager.findProductIdOptional(this.name);

        if (newProductID.isPresent()) {
            Util.logMessage("Successfully fixed product ID for " + this.name + ": " + newProductID);

            return true;
        } else {
            Util.logError("While refinding product id, could not find product ID for " + this.name, null);

            return false;
        }
    }

    //TODO this ideally isn't needed -- fix any bugs that cause these issues in the first place
    private boolean isProductIDHealthy() {
        return !(this.productID == null || this.productID.isEmpty() || BazaarDataManager.findItemPriceOptional(this.productID, getOrderType()).isEmpty());
    }

    /**
     * Determines whether the order price is competitive, matched, or outbid relative to the market.
     *
     * @return status reflecting how this order compares to current prices, if calculable
     */
    public Optional<PricingPosition> findPricingPosition() {
        if (this.pricePerItem == null || !isProductIDHealthy()) {
            return Optional.empty();
        }

        updateMarketPrice();

        double marketPrice = getPriceForPosition(PricingPosition.MATCHED, getOrderType());

        var orderCountOpt = BazaarDataManager.getOrderCountOptional(productID, getOrderType(), getPricePerItem());

        if (orderCountOpt.isEmpty()) {
            return Optional.empty();
        }

        int orderCount = orderCountOpt.getAsInt();

        if (orderType == OrderType.BUY) {
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

    @Override
    public void subscribe() {
        EVENT_BUS.subscribe(this);
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
        OrderType orderType = other.getOrderType();

        if (isStrict) {
            return isStrictlySimilarTo(otherOrderName, otherOrderPrice, otherOrderVolume, orderType);
        }

        return isLooselySimilarTo(otherOrderName, otherOrderPrice, otherOrderVolume, otherOrderAmountUnclaimed, orderType);
    }

    private boolean isStrictlySimilarTo(String otherOrderName, Double otherOrderPrice, Integer otherOrderVolume, OrderType orderType) {
        return (areAnyNull(this.pricePerItem, otherOrderPrice) || isSimilarPrice(otherOrderPrice)) &&
                (areAnyNull(this.volume, otherOrderVolume) || this.volume.equals(otherOrderVolume)) &&
                (areAnyNull(this.name, otherOrderName) || this.name.equalsIgnoreCase(otherOrderName)) &&
                (areAnyNull(this.orderType, orderType) || this.orderType == orderType);
    }

    private boolean isLooselySimilarTo(String otherOrderName, Double otherOrderPrice, Integer otherOrderVolume, int otherOrderAmountUnclaimed, OrderType orderType) {
        return (areAnyNull(this.pricePerItem, otherOrderPrice) || this.isSimilarPrice(otherOrderPrice)) &&
                (areAnyNull(this.volume, otherOrderVolume) || Util.genericIsSimilarValue(this.getVolume(), otherOrderVolume, 0.05 * otherOrderVolume) || this.getVolume().equals(otherOrderAmountUnclaimed)) && // sometimes the only volume that can be found is the amount that is unclaimed, like in FlipHelper
                (areAnyNull(this.name, otherOrderName) || this.getName().equalsIgnoreCase(otherOrderName)) &&
                (areAnyNull(this.orderType, orderType) || this.getOrderType() == orderType);
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

    @Override
    public String toString() {
        return "(name: " + this.name +
                ", price:" + this.pricePerItem +
                ", volume: " + this.volume +
                ")";
    }

    /**
     * Converts the current container into a fully tracked {@link Order}.
     */
    public Order toBazaarOrder() {
        return new Order(name, volume, pricePerItem, orderType, null);
    }
}
