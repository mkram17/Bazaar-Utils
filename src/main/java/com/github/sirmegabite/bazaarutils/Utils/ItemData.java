package com.github.sirmegabite.bazaarutils.Utils;

import scala.Int;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.github.sirmegabite.bazaarutils.configs.BUConfig.watchedItems;

public class ItemData {
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProductID() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    private String name;
    private String productId;

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
    public double getMarketPrice() {
        return marketPrice;
    }

    public void setMarketPrice(double marketPrice) {
        this.marketPrice = marketPrice;
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

    public void setVolume(int volume) {
        this.volume = volume;
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

    //insta sell and insta buy
    private double price;
    private boolean isCopied = false;
    private priceTypes priceType;
    //the sell or buy price of lowest/highest offer
    private double marketPrice;
    //item price * volume
    private double fullPrice;
    private statuses status;
    private int volume;

    private int amountClaimed;
    private int amountFilled;

    //lists
    public static ArrayList<Double> prices = getVariables(ItemData::getPrice);
    public static ArrayList<Integer> volumes = getVariables(ItemData::getVolume);
    public static ArrayList<String> names = getVariables(ItemData::getName);
    public static ArrayList<Integer> amountClaimeds = getVariables(ItemData::getAmountClaimed);

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
        for(ItemData item: watchedItems) {
//            System.out.println("trying to update item: " + item.getName());
            item.marketPrice = BazaarData.findItemPrice(item.productId, item.priceType);
//            System.out.println("new buy price: " +  BazaarData.findItemPrice(item.productId, "buyPrice"));

        }
        updateLists();
    }

    private static void updateLists(){
        prices = getVariables(ItemData::getPrice);
        volumes = getVariables(ItemData::getVolume);
        names = getVariables(ItemData::getName);
        amountClaimeds = getVariables(ItemData::getAmountClaimed);
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

    public static int findIndex(String name, Double price, Integer volume) {
        return IntStream.range(0, watchedItems.size())
                .filter(i -> (price == null || Util.isSimilar(prices.get(i), price)) &&
                        (volume == null || volumes.get(i) == volume + amountClaimeds.get(i)) &&
                        (name == null || name.equalsIgnoreCase(names.get(i))))
                .findFirst()
                .orElse(-1);
    }

//    public static int findIndex(double price, int volume) {
//        for (int i = 0; i < watchedItems.size(); i++) {
//            if (Util.isSimilar(prices.get(i), price) && (volumes.get(i) - amountClaimeds.get(i)) == volume) {
//                return i;
//            }
//        }
//        return -1;
//    }

//    public static int findIndex(double price, int volume, String name) {
//        for (int i = 0; i < watchedItems.size(); i++) {
//            if (Util.isSimilar(prices.get(i), price) && volumes.get(i) == volume && name.equalsIgnoreCase(names.get(i))) {
//                return i;
//            }
//        }
//        return -1;
//    }
//
//    public static int findIndex(String name, int volume) {
//        for (int i = 0; i < names.size(); i++) {
//            if (names.get(i).equalsIgnoreCase(name) && volumes.get(i) == volume) {
//                return i;
//            }
//        }
//        return -1;
//    }

    public static ItemData getItem(int index){
        return watchedItems.get(index);
    }

    public double getFlipPrice(){
        if (priceType == ItemData.priceTypes.INSTABUY) {
            return (BazaarData.findItemPrice(name, priceTypes.INSTASELL) + .1);
        } else {
            return (BazaarData.findItemPrice(productId, priceTypes.INSTABUY) - .1);
        }
    }
    public void setCopied(){
        for(ItemData item : watchedItems){
            if(item.isCopied) {
                Util.notifyConsole("Another item is already copied. Uncopying that one.");
                item.isCopied = false;
            }
        }
        this.isCopied = true;
    }

    public void unsetCopied(){
        this.isCopied = false;
    }

    public static void setItemFilled(ItemData item){
        item.amountFilled = item.volume;
        item.status = statuses.FILLED;
    }

    public static void removeItem(ItemData item){
        watchedItems.remove(item);
    }

}
