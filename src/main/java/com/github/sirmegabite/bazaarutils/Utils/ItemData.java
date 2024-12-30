package com.github.sirmegabite.bazaarutils.Utils;

import java.util.ArrayList;

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

    //insta sell and insta buy
    private double price;
    private boolean isCopied = false;

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



    private priceTypes priceType;
    //the sell or buy price of lowest/highest offer
    private double marketPrice;
    //item price * volume
    private double fullPrice;

    public enum statuses{SET,FILLED}
    private statuses status;
    private int volume;
    private int amountFilled;

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

    }


    public static int findFromVolume(int volume){
        int i = 0;
        for(ItemData item : watchedItems){
            if(item.getVolume() == volume) return i;
            i++;
        }
        return -1;
    }

    public static int findFromPrice(double price){
        int i = 0;
        for(ItemData item : watchedItems){
            if(item.getPrice() == price) return i;
            i++;
        }
        return -1;
    }

    public static ArrayList<String> getNames(){
        ArrayList<String> itemNames = new ArrayList<>();
        for(ItemData item : watchedItems){
            itemNames.add(item.getName());
        }
        return itemNames;
    }
    public static ArrayList<Integer> getVolumes(){
        ArrayList<Integer> itemPrices = new ArrayList<>();
        for(ItemData item : watchedItems){
            itemPrices.add(item.getVolume());
        }
        return itemPrices;
    }
    public static ArrayList<Double> getPrices(){
        ArrayList<Double> itemPrices = new ArrayList<>();
        for(ItemData item : watchedItems){
            itemPrices.add(item.getPrice());
        }
        return itemPrices;
    }

    public static int findIndex(double price, int volume){
        int index = -1;
        //if any watchedItems have the same price and volume to what is being searched for
        for (int i = 0; i < watchedItems.size(); i++) {
            if (Util.isSimilar(getPrices().get(i),price) && getVolumes().get(i)==volume) {
                index = i;
                break;
            }
        }
        return index;
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



    public static void setItemFilled(String name, int volume){
        ArrayList<String> itemNames = getNames();
        ArrayList<Integer> itemVolumes = getVolumes();
        for(int i = 0; i < itemNames.size(); i++){
            if(itemNames.get(i).equalsIgnoreCase(name) && itemVolumes.get(i) == volume){
                watchedItems.get(i).setStatus(statuses.FILLED);
            }
        }
    }
    public static void removeItem(String name, int volume, double price){
        ArrayList<String> itemNames = getNames();
        ArrayList<Integer> itemVolumes = getVolumes();
        ArrayList<Double> itemPrices = getPrices();
        for(int i = 0; i < itemNames.size(); i++){
            if(itemNames.get(i).equalsIgnoreCase(name) && itemVolumes.get(i) == volume && itemPrices.get(i) == price){
                watchedItems.remove(i);
            }
        }
    }

}
