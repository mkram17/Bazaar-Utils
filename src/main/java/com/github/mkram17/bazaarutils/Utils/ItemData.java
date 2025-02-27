package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.OutdatedItemEvent;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.BazaarData;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


//TODO figure out how to handle rounding with price
@Slf4j
public class ItemData {
    public static ItemData getItem(int index){
        if(index != -1)
            return BUConfig.get().watchedItems.get(index);
        else return null;
    }

    public String getProductID() {
        return productId;
    }

    @Getter
    private final String name;
    private final String productId;

    public int getIndex(){return BUConfig.get().watchedItems.indexOf(this);}

    public String getGeneralInfo(){
        String str = "(name: " + name + "[" + getIndex() + "]" + ", price:" + price + ", volume: " + volume;
        if(amountClaimed != 0)
            str += ", amount claimed: " + amountClaimed;
        str += ", type: " + priceType;
        if(status == statuses.FILLED)
            str += ", status: " + status;
        str +=  ")";
        return str;
    }

    @Getter
    public enum priceTypes{INSTASELL,INSTABUY;
        private priceTypes opposite;
        static {
            INSTASELL.opposite = INSTABUY;
            INSTABUY.opposite = INSTASELL;
        }
    }
    public enum statuses{SET,FILLED}

    static ScheduledExecutorService timeExecutor = Executors.newScheduledThreadPool(5);
    private static int notifyOutdatedSeconds = 0;

    //insta sell and insta buy
    @Setter
    @Getter
    private double price;
    @Setter
    @Getter
    private priceTypes priceType;
    //the sell or buy price of lowest/highest offer
    @Getter
    private double marketPrice;
    //the price of the opposite type of order
    private double marketOppositePrice;
    //item price * volume
    private final double fullPrice;
    @Setter
    @Getter
    private statuses status;
    @Getter
    private final int volume;

    @Setter
    @Getter
    private int amountClaimed = 0;
    @Setter
    @Getter
    private int amountFilled = 0;
    private double maximumRounding;

    private static List<ItemData> outdated = new ArrayList<>(Collections.emptyList());

    public ItemData(String name, Double fullPrice, priceTypes priceType, int volume) {
        this.name = name;
        this.priceType = priceType;
        this.fullPrice = fullPrice;
        this.productId = BazaarData.findProductId(name);
        this.volume = volume;
        this.price = fullPrice/volume;
        this.status = statuses.SET;
        this.maximumRounding = getMaxRouding(fullPrice, volume);
    }

    private static double getMaxRouding(double fullPrice, int volume){
        if(fullPrice < 10000)
            return 0;
        else{
            return (Math.ceil((.9 / volume) * 10))/10;
        }
    }


    public static void update(){
        updateMarketPrices();
        findOutdated();
    }

    public static void scheduleNotifyOutdated(){
        //if its a decimal, it will schedule decimal for every second as ex: .3 = every 3 seconds
        if(BUConfig.get().outdatedItems.isNotifyOutdated()) {
            timeExecutor.scheduleAtFixedRate(ItemData::notifyOutdated, 0, 1, TimeUnit.SECONDS);
        }
    }

    private static void updateMarketPrices(){
        for(ItemData item: BUConfig.get().watchedItems) {
            double oldPrice = item.marketPrice;
            item.marketPrice = Util.getPrettyNumber(BazaarData.findItemPrice(item.productId, item.priceType));
            item.marketOppositePrice = Util.getPrettyNumber(BazaarData.findItemPrice(item.productId, item.priceType.getOpposite()));
            if(oldPrice != item.marketPrice)
                Util.notifyAll(item.getGeneralInfo() + " has new market price: " + item.getMarketPrice(), Util.notificationTypes.BAZAARDATA);
        }
    }

    public void flipItem(double newPrice){
        this.priceType = this.priceType.getOpposite();
        this.price = newPrice;
        this.amountFilled = 0;
        this.status = statuses.SET;
    }

    //TODO some error with maximum rounding or finding the price. either finding price can round down by .1 accidentally or maximum rounding calculation is wrong
    public boolean isSimilarPrice(double price) {
        return Math.abs(getPrice() - price) <= maximumRounding;
    }

    //untested
    //run by ex: getVariables((item) -> item.getPrice()) or (chatgpt) ItemData.getVariables(ItemData::getPrice);
    public static <T> ArrayList<T> getVariables(Function<ItemData, T> variable){
        ArrayList<T> variables = new ArrayList<>();
        for(ItemData item : BUConfig.get().watchedItems){
            variables.add(variable.apply(item));
        }
        return variables;
    }

    public static ItemData findItem(String name, Double price, Integer volumeLeft, priceTypes priceType) {
        ArrayList<ItemData> itemList = new ArrayList<>();
        for(ItemData item : BUConfig.get().watchedItems){
            if((price == null || item.isSimilarPrice(price)) &&
                    (volumeLeft == null || item.getVolume() == volumeLeft + item.getAmountClaimed()) &&
                    (name == null || name.equalsIgnoreCase(item.getName())) &&
                    (priceType == null || priceType == item.getPriceType())){
                itemList.add(item);
            }
        }
        if (itemList.isEmpty()) {
            Util.notifyAll("Could not find item with info: [name: " + name + ", price: " + price + ", volume: " + volumeLeft + "]", Util.notificationTypes.ITEMDATA);
            return null;
        }
        if (itemList.size() > 1) {
            itemList.forEach(duplicate -> {
                Util.notifyAll("Duplicate item: " + duplicate.getGeneralInfo(), Util.notificationTypes.ITEMDATA);
            });
        }
        return itemList.getFirst();
    }
    public static ItemData findItemTotalPrice(double totalPrice) {
        ArrayList<ItemData> itemList = new ArrayList<>();
        for(ItemData item : BUConfig.get().watchedItems){
            if(totalPrice == item.fullPrice){
                itemList.add(item);
            }
        }
        if (itemList.isEmpty()) {
            Util.notifyAll("Could not find item with total price: " + totalPrice, Util.notificationTypes.ITEMDATA);
            return null;
        }
        if (itemList.size() > 1) {
            itemList.forEach(duplicate -> {
                Util.notifyAll("Duplicate totalprice item: " + duplicate.getGeneralInfo(), Util.notificationTypes.ITEMDATA);
            });
            return null;
        }
        return itemList.getFirst();
    }

    //maybe replace with using ItemOutdatedEvent?
    public static void notifyOutdated(){
        if(notifyOutdatedSeconds % BUConfig.get().outdatedItems.getOutdatedTiming() == 0 && BUConfig.get().outdatedItems.isNotifyOutdated()) {
            for (ItemData item : outdated) {
                Util.notifyAll(item.getGeneralInfo() + " is outdated.");
            }
        }
        notifyOutdatedSeconds++;
    }

    private static void findOutdated(){
        List<ItemData> oldOutdated = new ArrayList<>(outdated);
        outdated.clear();
        for(ItemData item: BUConfig.get().watchedItems){
            if(item.isOutdated()) {
                outdated.add(item);
                if(!oldOutdated.contains(item))
                    BazaarUtils.eventBus.post(new OutdatedItemEvent(item));
            }
        }
    }

    private boolean isOutdated(){
        if(status == statuses.FILLED)
            return false;
        if (priceType == priceTypes.INSTABUY) {
            return this.price-maximumRounding > this.marketPrice;
        } else {
            return this.price+maximumRounding < this.marketPrice;
        }
    }

    public double getFlipPrice(){
        updateMarketPrices();
        if (priceType == priceTypes.INSTABUY) {
            return (marketOppositePrice + .1);
        } else {
            return (marketOppositePrice - .1);
        }
    }

    public static void setItemFilled(ItemData item){
        item.amountFilled = item.volume;
        item.status = statuses.FILLED;
    }

    public static void removeItem(ItemData item){
        BUConfig.get().watchedItems.remove(item);
    }
    public void remove(){
        BUConfig.get().watchedItems.remove(this);
    }
    public static void clearItems(){
        for(ItemData item: BUConfig.get().watchedItems)
            removeItem(item);
    }
}
