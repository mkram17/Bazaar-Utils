package com.github.mkram17.bazaarutils.misc.orderinfo;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.BazaarData;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
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
 * {@link BazaarOrder} lifecycle.
 */
//TODO turn into builder class
public class OrderInfoContainer extends PriceInfoContainer implements BUListener {

    private static final double DEFAULT_TOLERANCE = 0.9;
    private static final double TOTAL_PRICE_ROUNDING_THRESHOLD = 10000;

    /**
     * Represents how an order compares to current market activity or fulfillment status.
     */
    public enum Statuses {SET, FILLED, OUTBID, COMPETITIVE, MATCHED}

    @Getter
    protected final String name; //name of the item in game
    @Getter
    protected final Integer volume;
    @Getter
    protected String productID; //Hypixel's code for the product
    @Getter
    protected Statuses outbidStatus;
    @Getter @Setter
    protected double tolerance; //When finding item price, it can round to the nearest coin sometimes, so tolerance is needed for price calculations
    @Getter @Setter
    private ItemInfo itemInfo;

    /**
     * Creates a container that tracks market data for a specific Bazaar product.
     *
     * @param name         display name of the item
     * @param volume       quantity of the order
     * @param pricePerItem current price per unit for the order
     * @param priceType    whether the price represents an insta-sell (buy order) or insta-buy (sell order)
     * @param itemInfo     optional UI context from the Bazaar screen
     */
    public OrderInfoContainer(@Nullable String name, @Nullable Integer volume, @Nullable Double pricePerItem, @Nullable PriceType priceType, @Nullable ItemInfo itemInfo) {
        super(pricePerItem, priceType);
        this.volume = volume;
        this.name = name;
        this.tolerance = calculateTolerance();
        this.itemInfo = itemInfo;

        BazaarData.findProductIdOptional(name).ifPresent(productId -> this.productID = productId);
        validateProduct();
        findOutbidStatus().ifPresent(status -> this.outbidStatus = status);
    }

    private double calculateTolerance() {
        //default tolerance
        if (pricePerItem == null || volume == null) {
            return DEFAULT_TOLERANCE;
        }
        //doesn't round prices when the total is over 10k
        if (pricePerItem * volume < TOTAL_PRICE_ROUNDING_THRESHOLD) {
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
    public static boolean isValidName(String itemName){
        return itemName != null && BazaarData.findProductIdOptional(itemName).isPresent();
    }

    /**
     * Refreshes cached market price data for this product.
     */
    public void updateMarketPrice(){
        updateMarketPrice(productID);
    }

    private void validateProduct(){
        if (productID == null && name != null) {
            if(!fixProductID()){
                Util.notifyError("Product ID for " + name + " is null. This may cause issues", new Throwable());
            }
        }
    }

    protected void scheduleHealthCheck() {
        long START_DELAY_SECONDS = 60;
        long CHECK_INTERVAL_SECONDS = 30;
        BazaarUtils.BUExecutorService.scheduleAtFixedRate(() -> {
            if(!fixProductID()){
                Util.logError("Could not fix product ID for " + name + ". This may cause the mod to work improperly.", new Throwable());
            }
        }, START_DELAY_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }


    //returns true if productID is safe/fixed after run, and false if it is not
    private boolean fixProductID() {
        if (isProductIDHealthy()) {
            return true;
        }
        Optional<String> newProductID = BazaarData.findProductIdOptional(name);
        if (newProductID.isPresent()) {
            Util.logMessage("Successfully fixed product ID for " + name + ": " + newProductID);
            return true;
        } else {
            Util.logError("While refinding product id, could not find product ID for " + name, null);
            return false;
        }
    }

    //TODO this ideally isn't needed -- fix any bugs that cause these issues in the first place
    private boolean isProductIDHealthy() {
        return !(productID == null || productID.isEmpty() || BazaarData.findItemPriceOptional(productID, getPriceType()).isEmpty());
    }

    /**
     * Determines whether the order price is competitive, matched, or outbid relative to the market.
     *
     * @return status reflecting how this order compares to current prices, if calculable
     */
    public Optional<Statuses> findOutbidStatus() {
        if(pricePerItem == null || !isProductIDHealthy()) return Optional.empty();
        updateMarketPrice();
        double marketPrice = getMarketPrice(getPriceType());

        var orderCountOpt = BazaarData.getOrderCountOptional(productID, getPriceType(), getPricePerItem());
        if(orderCountOpt.isEmpty()) return Optional.empty();
        int orderCount = orderCountOpt.getAsInt();

        if(priceType == PriceType.INSTASELL){
            if(pricePerItem > marketPrice){
                return Optional.of(Statuses.COMPETITIVE);
            } else if(pricePerItem < marketPrice){
                return Optional.of(Statuses.OUTBID);
            } else {
                if (orderCount > 1) {
                    return Optional.of(Statuses.MATCHED);
                }
            }
        } else {
            if(pricePerItem < marketPrice){
                return Optional.of(Statuses.COMPETITIVE);
            } else if(pricePerItem > marketPrice){
                return Optional.of(Statuses.OUTBID);
            } else {
                if (orderCount > 1) {
                    return Optional.of(Statuses.MATCHED);
                }
            }
        }

        return Optional.of(Statuses.COMPETITIVE);
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
    public boolean isSimilarTo(BazaarOrder other, boolean isStrict) {
        String otherOrderName = other.getName();
        Double otherOrderPrice = other.getPricePerItem();
        Integer otherOrderVolume = other.getVolume();
        int otherOrderAmountUnclaimed = other.getAmountFilled() - other.getAmountClaimed();
        PriceType priceType = other.getPriceType();

        if (isStrict) {
            return isStrictlySimilarTo(otherOrderName, otherOrderPrice, otherOrderVolume, priceType);
        }
        return isLooselySimilarTo(otherOrderName, otherOrderPrice, otherOrderVolume, otherOrderAmountUnclaimed, priceType);
    }

    private boolean isStrictlySimilarTo(String otherOrderName, Double otherOrderPrice, Integer otherOrderVolume, PriceType priceType) {
        return (areAnyNull(this.pricePerItem, otherOrderPrice) || isSimilarPrice(otherOrderPrice)) &&
                (areAnyNull(this.volume, otherOrderVolume) || this.volume.equals(otherOrderVolume)) &&
                (areAnyNull(this.name, otherOrderName) || this.name.equalsIgnoreCase(otherOrderName)) &&
                (areAnyNull(this.priceType, priceType) || this.priceType == priceType);
    }

    private boolean isLooselySimilarTo(String otherOrderName, Double otherOrderPrice, Integer otherOrderVolume, int otherOrderAmountUnclaimed, PriceType priceType) {
        return (areAnyNull(this.pricePerItem, otherOrderPrice) || this.isSimilarPrice(otherOrderPrice)) &&
                (areAnyNull(this.volume, otherOrderVolume) || Util.genericIsSimilarValue(this.getVolume(), otherOrderVolume, 0.05 * otherOrderVolume) || this.getVolume().equals(otherOrderAmountUnclaimed)) && // sometimes the only volume that can be found is the amount that is unclaimed, like in FlipHelper
                (areAnyNull(this.name, otherOrderName) || this.getName().equalsIgnoreCase(otherOrderName)) &&
                (areAnyNull(this.priceType, priceType) || this.getPriceType() == priceType);
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
    public Optional<BazaarOrder> findOrderInList(List<BazaarOrder> list) {
        List<BazaarOrder> itemList = findAllMatchesInList(list);
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
    public List<BazaarOrder> findAllMatchesInList(List<BazaarOrder> list) {
        List<BazaarOrder> itemList = new ArrayList<>();
        for (BazaarOrder item : list) {
            if (this.isSimilarTo(item, true)) {
                itemList.add(item);
            }
        }
        if (itemList.isEmpty()) {
            for (BazaarOrder item : list) {
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
     * {@link BUConfig#userOrders}.
     *
     * @param <T>      type of value extracted from each order
     * @param variable accessor used to extract a value from each order
     * @return immutable list of extracted values
     */
    public static <T> List<T> getVariables(Function<BazaarOrder, T> variable) {
        return BUConfig.get().userOrders.stream()
                .map(variable)
                .toList();
    }
    /** Used for when there are duplicate matches found and the best should be chosen to use.
     * Typically, volume is the variable that is different, but it can also be price
    */
    private BazaarOrder findBestMatch(List<BazaarOrder> list) {
        return list.stream()
                .min(getVolumeThenPriceComparator())
                .orElse(list.getFirst());
    }

    private Comparator<BazaarOrder> getVolumeThenPriceComparator() {
        Comparator<BazaarOrder> volumeComparator = Comparator.comparingDouble(order -> {
            if (areAnyNull(this.getVolume(), order.getVolume())) {
                return Double.MAX_VALUE;
            }
            return Math.abs(order.getVolume() - this.getVolume());
        });

        Comparator<BazaarOrder> priceComparator = Comparator.comparingDouble(order -> {
            if (areAnyNull(this.pricePerItem, order.getPricePerItem())) {
                return Double.MAX_VALUE;
            }
            return Math.abs(order.getPricePerItem() - this.pricePerItem);
        });

        return volumeComparator.thenComparing(priceComparator);
    }

    @Override
    public String toString() {
        return "(name: " + name +
                ", price:" + pricePerItem +
                ", volume: " + volume +
                ")";
    }

    /**
     * Converts the current container into a fully tracked {@link BazaarOrder}.
     */
    public BazaarOrder toBazaarOrder(){
        return new BazaarOrder(name, volume, pricePerItem, priceType, null);
    }
}
