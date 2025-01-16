package com.github.sirmegabite.bazaarutils.Utils;

import com.github.sirmegabite.bazaarutils.configs.BUConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.sirmegabite.bazaarutils.configs.BUConfig.watchedItems;

public class ItemData {
    public static ItemData getItem(int index){
        if(index != -1)
         return watchedItems.get(index);
        else return null;
    }

    public String getName() {
        return name;
    }

    public String getProductID() {
        return productId;
    }

    private final String name;
    private final String productId;

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
    public double getMarketPrice() {
        return marketPrice;
    }

    public int getVolume() {
        return volume;
    }
    public priceTypes getPriceType() {
        return priceType;
    }

    public void setPriceType(priceTypes priceType) {
        this.priceType = priceType;
    }

    public statuses getStatus() {
        return status;
    }

    public void setStatus(statuses status) {
        this.status = status;
    }

    public int getAmountFilled() {
        return amountFilled;
    }

    public void setAmountFilled(int amountFilled) {
        this.amountFilled = amountFilled;
    }

    public int getAmountClaimed() {
        return amountClaimed;
    }

    public void setAmountClaimed(int amountClaimed) {
        this.amountClaimed = amountClaimed;
    }

    public int getIndex(){return watchedItems.indexOf(this);}

    public String getGeneralInfo(){
        String str = "(name: " + name + "[" + getIndex() + "]" + ", price:" + price + ", volume: " + volume;
        if(amountClaimed != 0)
            str += ", amount claimed: " + amountClaimed;
        str += ", type: " + priceType;
        str +=  ")";
        return str;
    }

    public enum priceTypes{INSTASELL,INSTABUY;
        private priceTypes opposite;
        static {
            INSTASELL.opposite = INSTABUY;
            INSTABUY.opposite = INSTASELL;
        }
        public priceTypes getOpposite(){
            return opposite;
        }
    }
    public enum statuses{SET,FILLED}

    static ScheduledExecutorService timeExecutor = Executors.newScheduledThreadPool(5);

    //insta sell and insta buy
    private double price;
    private final boolean isCopied = false;
    private priceTypes priceType;
    //the sell or buy price of lowest/highest offer
    private double marketPrice;
    //item price * volume
    private final double fullPrice;
    private statuses status;
    private final int volume;

    private int amountClaimed = 0;
    private int amountFilled = 0;

    //lists
    public static ArrayList<Double> prices = getVariables(ItemData::getPrice);
    public static ArrayList<Integer> volumes = getVariables(ItemData::getVolume);
    public static ArrayList<String> names = getVariables(ItemData::getName);
    public static ArrayList<Integer> amountClaimeds = getVariables(ItemData::getAmountClaimed);

    private static final List<ItemData> outdated = new ArrayList<>(Collections.emptyList());

    public ItemData(String name, Double price, priceTypes priceType, int volume) {
        this.name = name;
        this.priceType = priceType;
        this.price = price;
        this.productId = BazaarData.findProductId(name);
        this.volume = volume;
        this.fullPrice = price*volume;
        this.status = statuses.SET;
        updateLists();
    }


    public static void update(){
        updateMarketPrices();
        findOutdated();
    }

    public static void scheduleNotifyOutdated(int seconds){
        //if its a decimal, it will schedule decimal for every second as ex: .3 = every 3 seconds
        if(BUConfig.notifyOutdated) {
            timeExecutor.scheduleAtFixedRate(() ->notifyOutdated(seconds), 0, 1, TimeUnit.SECONDS);
        }
    }
    private static void updateLists(){
        prices = getVariables(ItemData::getPrice);
        volumes = getVariables(ItemData::getVolume);
        names = getVariables(ItemData::getName);
        amountClaimeds = getVariables(ItemData::getAmountClaimed);
    }

    private static void updateMarketPrices(){
        for(ItemData item: watchedItems) {
            item.marketPrice = BazaarData.findItemPrice(item.productId, item.priceType);
            Util.notifyAll(item.getGeneralInfo() + " has new market price: " + item.getMarketPrice(), Util.notificationTypes.BAZAARDATA);
        }
    }

    public static ArrayList<String> getNames(){
        ArrayList<String> itemNames = new ArrayList<>();
        for(ItemData item : watchedItems){
            itemNames.add(item.getName());
        }
        return itemNames;
    }

    //untested
    //run by ex: getVariables((item) -> item.getPrice()) or (chatgpt) ItemData.getVariables(ItemData::getPrice);
    public static <T> ArrayList<T> getVariables(Function<ItemData, T> variable){
        ArrayList<T> variables = new ArrayList<>();
        for(ItemData item : watchedItems){
            variables.add(variable.apply(item));
        }
        return variables;
    }

    public static ItemData findItem(String name, Double price, Integer volume) {
//        Util.notifyAll("Called from: " + Util.getCallingClassName(), Util.notificationTypes.ITEMDATA);
        List<Integer> matchingIndices = IntStream.range(0, watchedItems.size())
                .filter(i -> (price == null || Util.isSimilar(prices.get(i), price)) &&
                        (volume == null || volumes.get(i) == volume + amountClaimeds.get(i)) &&
                        (name == null || name.equalsIgnoreCase(names.get(i))))
                .boxed()
                .collect(Collectors.toList()); // Use Collectors.toList() for older Java versions.

        if (matchingIndices.isEmpty()) {
            Util.notifyAll("Could not find item with info: [name: " + name + ", price: " + price + ", volume: " + volume + "]", Util.notificationTypes.ITEMDATA);
            return null;
        }

        if (matchingIndices.size() > 1) {
            matchingIndices.forEach(index -> {
                ItemData duplicate = getItem(index);
                Util.notifyAll("Duplicate item: " + duplicate.getGeneralInfo(), Util.notificationTypes.ITEMDATA);
            });
        }

        ItemData item = getItem(matchingIndices.get(0)); // Get the first match.
        return item;
    }

    public static void notifyOutdated(int executionsPer){
        for(ItemData item: outdated){
            Util.notifyAll(item.getGeneralInfo() + " is outdated.");
        }
    }

    private static void findOutdated(){
        outdated.clear();
        for(ItemData item: watchedItems){
            if(!item.isOutdated())
                outdated.add(item);
        }
    }

    private boolean isOutdated(){
        if (priceType == priceTypes.INSTABUY) {
            return this.price > this.marketPrice;
        } else {
            return this.price < this.marketPrice;
        }
    }

    public double getFlipPrice(){
        if (priceType == priceTypes.INSTABUY) {
            return (BazaarData.findItemPrice(name, priceTypes.INSTASELL) + .1);
        } else {
            return (BazaarData.findItemPrice(productId, priceTypes.INSTABUY) - .1);
        }
    }

    public static void setItemFilled(ItemData item){
        item.amountFilled = item.volume;
        item.status = statuses.FILLED;
    }

    public static void removeItem(ItemData item){
        watchedItems.remove(item);
    }
    public void remove(){
        watchedItems.remove(this);
    }
    public static void clearItems(){
        for(ItemData item: watchedItems)
            removeItem(item);
    }


    }
