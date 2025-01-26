package com.github.mkram17.bazaarutils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        if(status == statuses.FILLED)
            str += ", status: " + status;
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
    private static int notifyOutdatedSeconds = 0;

    //insta sell and insta buy
    private double price;
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
    public static ArrayList<Double> priceList = getVariables(ItemData::getPrice);
    public static ArrayList<Integer> volumeList = getVariables(ItemData::getVolume);
    public static ArrayList<String> nameList = getVariables(ItemData::getName);
    public static ArrayList<Integer> amountClaimedList = getVariables(ItemData::getAmountClaimed);
    public static ArrayList<priceTypes> priceTypesList = getVariables(ItemData::getPriceType);

    private static List<ItemData> outdated = new ArrayList<>(Collections.emptyList());

    public ItemData(String name, Double price, priceTypes priceType, int volume) {
        this.name = name;
        this.priceType = priceType;
        this.price = price;
        this.productId = BazaarData.findProductId(name);
        this.volume = volume;
        this.fullPrice = price*volume;
        this.status = statuses.SET;
    }


    public static void update(){
        updateMarketPrices();
        updateLists();
        findOutdated();
    }

    public static void scheduleNotifyOutdated(){
        //if its a decimal, it will schedule decimal for every second as ex: .3 = every 3 seconds
        if(BUConfig.notifyOutdated) {
            timeExecutor.scheduleAtFixedRate(ItemData::notifyOutdated, 0, 1, TimeUnit.SECONDS);
        }
    }
    public static void updateLists(){
        priceList = getVariables(ItemData::getPrice);
        volumeList = getVariables(ItemData::getVolume);
        nameList = getVariables(ItemData::getName);
        amountClaimedList = getVariables(ItemData::getAmountClaimed);
        priceTypesList = getVariables(ItemData::getPriceType);
    }

    private static void updateMarketPrices(){
        for(ItemData item: watchedItems) {
            double oldPrice = item.marketPrice;
            item.marketPrice = Util.getPrettyNumber(BazaarData.findItemPrice(item.productId, item.priceType));
            if(oldPrice != item.marketPrice)
                Util.notifyAll(item.getGeneralInfo() + " has new market price: " + item.getMarketPrice(), Util.notificationTypes.BAZAARDATA);
        }
    }

    public void flipItem(){
        this.priceType = this.priceType.getOpposite();
        this.price = this.getFlipPrice();
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

    public static ItemData findItem(String name, Double price, Integer volume, priceTypes priceType) {
//        Util.notifyAll("Called from: " + Util.getCallingClassName(), Util.notificationTypes.ITEMDATA);
        List<Integer> matchingIndices = IntStream.range(0, watchedItems.size())
                .filter(i -> (price == null || Util.isSimilar(priceList.get(i), price)) &&
                        (volume == null || volumeList.get(i) == volume + amountClaimedList.get(i)) &&
                        (name == null || name.equalsIgnoreCase(nameList.get(i))) &&
                        (priceType == null || priceType == priceTypesList.get(i)))
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

    //maybe replace with using ItemOutdatedEvent?
    public static void notifyOutdated(){
        if(notifyOutdatedSeconds % outdatedTiming == 0) {
            for (ItemData item : outdated) {
                Util.notifyAll(item.getGeneralInfo() + " is outdated.");
            }
        }
        notifyOutdatedSeconds++;
    }

    private static void findOutdated(){
        List<ItemData> oldOutdated = outdated;
        outdated.clear();
        for(ItemData item: watchedItems){
            if(item.isOutdated()) {
                outdated.add(item);
                if(!oldOutdated.contains(item))
                    MinecraftForge.EVENT_BUS.post(new OutdatedItemEvent(item));
            }
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
        updateMarketPrices();
        if (priceType == priceTypes.INSTABUY) {
            return (marketPrice + .1);
        } else {
            return (marketPrice - .1);
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
