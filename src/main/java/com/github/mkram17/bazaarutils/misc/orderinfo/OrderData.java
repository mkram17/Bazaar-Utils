package com.github.mkram17.bazaarutils.misc.orderinfo;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.BazaarData;
import com.github.mkram17.bazaarutils.events.OutdatedItemEvent;
import com.github.mkram17.bazaarutils.utils.Util;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;


//TODO figure out how to handle rounding with price
//TODO use last viewed item in bazaar to help with finding accurate price instead of just chat message
@Slf4j
public class OrderData {
    public String getProductID() {
        return productId;
    }

    @Getter
    private final String name;
    private final String productId;

    public int getIndex(){return BUConfig.get().watchedOrders.indexOf(this);}

    public String getGeneralInfo(){
        String str = "(name: " + name + "[" + getIndex() + "]" + ", price:" + priceInfo.getPrice() + ", volume: " + volume;
        if(amountClaimed != 0)
            str += ", amount claimed: " + amountClaimed;
        str += ", type: " + priceInfo.getPriceType().getString() + " order";
        if(fillStatus == statuses.FILLED)
            str += ", status: " + fillStatus;
        str +=  ")";
        return str;
    }

    public enum statuses{SET,FILLED, OUTDATED, COMPETITIVE, MATCHED}
    @Setter @Getter
    private statuses fillStatus;
    @Getter
    private final int volume;

    @Setter
    @Getter
    private int amountClaimed = 0;
    @Setter
    @Getter
    private int amountFilled = 0;
    @Getter @Setter
    private double maximumRounding;
    @Getter
    private OrderPriceInfo priceInfo;

    @Getter
    private static final List<OrderData> outdatedItems = new ArrayList<>(Collections.emptyList());

//    @Param fullPrice is the price per unit * volume
    //When finding item price, it can round to the nearest coin sometimes, so maximumRounding is used to determine if the price is similar enough to be considered a match
    @Deprecated
    public OrderData(String name, Double fullPrice, OrderPriceInfo.priceTypes priceType, int volume) {
        priceInfo = new OrderPriceInfo(fullPrice/volume, priceType);

        this.name = name;
        this.productId = BazaarData.findProductId(name);
        this.volume = volume;
        this.fillStatus = statuses.SET;
        this.maximumRounding = getMaxRounding(fullPrice, volume);

        if(productId == null){
            Util.notifyError("Could not find product id for item: " + name, null);
        }
    }
    public OrderData(String name, int volume, OrderPriceInfo priceInfo) {
        this.name = name;
        this.volume = volume;
        this.productId = BazaarData.findProductId(name);
        this.fillStatus = statuses.SET;
        this.priceInfo = priceInfo;
        this.maximumRounding = getMaxRounding(priceInfo.getPrice(), volume);



        if(productId == null){
            Util.notifyAll("Could not find product id for item: " + name, Util.notificationTypes.ITEMDATA);
        }
    }

    private static double getMaxRounding(double price, int volume){
        if(price*volume < 10000)
            return 0;
        else{
            return (Math.ceil((.9 / volume) * 10))/10;
        }
    }

    public void flipItem(double newPrice){
        priceInfo.flipPrices(newPrice);
        this.amountFilled = 0;
        this.fillStatus = statuses.SET;
    }

    //TODO some error with maximum rounding or finding the price. either finding price can round down by .1 accidentally or maximum rounding calculation is wrong
    public boolean isSimilarPrice(double price) {
        return Math.abs(priceInfo.getPrice() - price) <= maximumRounding;
    }

    //run by ex: getVariables((item) -> item.getPrice()) orItemData.getVariables(ItemData::getPrice);
    public static <T> ArrayList<T> getVariables(Function<OrderData, T> variable){
        ArrayList<T> variables = new ArrayList<>();
        for(OrderData item : BUConfig.get().watchedOrders){
            variables.add(variable.apply(item));
        }
        return variables;
    }

    //TODO refactor of match finding -- it can definitely be improved
    private static ArrayList<OrderData> findExactMatches(String name, Double price, Integer volume, OrderPriceInfo.priceTypes priceType){
        ArrayList<OrderData> itemList = new ArrayList<>();
        for(OrderData item : BUConfig.get().watchedOrders){
            if((price == null || item.isSimilarPrice(price)) &&
                    (volume == null || Math.abs(item.getVolume() - volume) <= (0.05 * volume)) &&
                    (name == null || name.equalsIgnoreCase(item.getName())) &&
                    (priceType == null || priceType == item.getPriceInfo().getPriceType())){
                itemList.add(item);
            }
        }
        return itemList;
    }
    private static ArrayList<OrderData> findLooseVolumeMatches(String name, Double price, Integer volume, OrderPriceInfo.priceTypes priceType){
        ArrayList<OrderData> itemList = new ArrayList<>();
        for(OrderData item : BUConfig.get().watchedOrders){
            if((price == null || item.isSimilarPrice(price)) &&
                    (volume == null || Math.abs(item.getVolume() - volume) <= (0.05 * volume) || Math.abs(item.getVolume()-item.getAmountClaimed() - volume) <= (0.05 * volume)) &&
                    (name == null || name.equalsIgnoreCase(item.getName())) &&
                    (priceType == null || priceType == item.getPriceInfo().getPriceType())){
                itemList.add(item);
            }
        }
        return itemList;
    }

    public static OrderData findItem(String name, Double price, Integer volume, OrderPriceInfo.priceTypes priceType) {
        ArrayList<OrderData> itemList = findExactMatches(name, price, volume, priceType);
        if(itemList.isEmpty())
            itemList = findLooseVolumeMatches(name, price, volume, priceType);

        if (itemList.isEmpty()) {
            Util.notifyAll("Could not find item with info: [name: " + name + ", price: " + price + ", volume: " + volume + "]", Util.notificationTypes.ITEMDATA);
            return null;
        }
        if (itemList.size() > 1) {
            OrderData bestMatch = itemList.getFirst();
            for (OrderData duplicate : itemList) {
                Util.notifyAll("Duplicate item: " + duplicate.getGeneralInfo(), Util.notificationTypes.ITEMDATA);
                if (volume == null) {
                    continue;
                }
                if (Math.abs(duplicate.getVolume() - volume) < Math.abs(bestMatch.getVolume() - volume)) {
                    bestMatch = duplicate;
                }
            }
            return bestMatch;
        }

        return itemList.getFirst();
    }
    public static OrderData findItem(OrderData matchingItem, List<OrderData> list) {
        String name = matchingItem.getName();
        double price = matchingItem.getPriceInfo().getPrice();
        int volume = matchingItem.getVolume();
        OrderPriceInfo.priceTypes priceType = matchingItem.getPriceInfo().getPriceType();
        ArrayList<OrderData> itemList = new ArrayList<>();
        for(OrderData item : list){
            if(item.isSimilarPrice(price) &&
                    item.getVolume() == volume &&
                    name.equalsIgnoreCase(item.getName()) &&
                    priceType == item.getPriceInfo().getPriceType()){
                itemList.add(item);
            }
        }
        if (itemList.isEmpty()) {
            return null;
        }
        if (itemList.size() > 1) {
            itemList.forEach(duplicate -> {
                Util.notifyAll("Duplicate item: " + duplicate.getGeneralInfo(), Util.notificationTypes.ITEMDATA);
            });
        }
        return itemList.getFirst();
    }

    public static void updateOutdatedItems() {
        List<OrderData> previousOutdatedItems = new ArrayList<>(outdatedItems);
        outdatedItems.clear();
        for (OrderData item : BUConfig.get().watchedOrders) {
            if (item.getOutdatedStatus() == statuses.OUTDATED) {
                outdatedItems.add(item);
            }
        }

        if (outdatedItems.isEmpty()) {
//            Util.notifyAll("No outdated items found.", Util.notificationTypes.ITEMDATA);
            return;
        }

        List<OrderData> availableOldOutdated = new ArrayList<>(previousOutdatedItems);

        for (OrderData currentNewOutdatedItem : outdatedItems) {
            boolean foundMatchInOldList = false;
            OrderData matchedOldItem = null;

            for (OrderData oldItem : availableOldOutdated) {
                if (currentNewOutdatedItem.getName().equals(oldItem.getName()) &&
                        Math.abs(currentNewOutdatedItem.getPriceInfo().getPrice() - oldItem.getPriceInfo().getPrice()) <= currentNewOutdatedItem.maximumRounding &&
                        currentNewOutdatedItem.getVolume() == oldItem.getVolume() &&
                        currentNewOutdatedItem.getPriceInfo().getPriceType() == oldItem.getPriceInfo().getPriceType()) {
                    foundMatchInOldList = true;
                    matchedOldItem = oldItem;
                    break;
                }
            }

            if (foundMatchInOldList) {
                availableOldOutdated.remove(matchedOldItem);
            } else {
                BazaarUtils.eventBus.post(new OutdatedItemEvent(currentNewOutdatedItem));
            }
        }

        for (OrderData noLongerOutdatedItem : availableOldOutdated) {
            if (BUConfig.get().watchedOrders.contains(noLongerOutdatedItem) && noLongerOutdatedItem.getFillStatus() != statuses.FILLED) {
                Text amount = Text.literal(noLongerOutdatedItem.getVolume() + "x ").formatted(Formatting.BOLD).formatted(Formatting.DARK_PURPLE);
                Text itemName = Text.literal(noLongerOutdatedItem.getName().formatted(Formatting.BOLD).formatted(Formatting.GOLD));
                MutableText message = Text.literal("[Bazaar Utils] ").formatted(Formatting.GOLD)
                        .append(Text.literal("Your " + noLongerOutdatedItem.getPriceInfo().getPriceType().getString().toLowerCase() + "order for ").formatted(Formatting.WHITE))
                        .append(amount)
                        .append(itemName)
                        .append(Text.literal( " is no longer outdated.").formatted(Formatting.WHITE));
                if(MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(message, false);
                } else {
                    // Queue the message to be sent when player is available
                    Util.tickExecuteLater(20, () -> {
                        if (MinecraftClient.getInstance().player != null) {
                            MinecraftClient.getInstance().player.sendMessage(message, false);
                        }
                    });
                }
            }
        }

    }

    public void updateMarketPrice(){
        if(productId == null){
            Util.notifyError("Could not find market price for " + name + ". If this keeps happening, please report to the developer to fix. You can disable error notifications in settings", null);
            return;
        }
        priceInfo.updateMarketPrice(productId);
    }

    public statuses getOutdatedStatus(){
        updateMarketPrice();
        if(fillStatus == statuses.FILLED)
            return statuses.FILLED;
        if(priceInfo.getPrice() == priceInfo.getMarketPrice() && maximumRounding == 0 && BazaarData.getOrderCount(productId, priceInfo.getPriceType(), priceInfo.getPrice()) > 1)
            return statuses.MATCHED;
        if (priceInfo.getPriceType() == OrderPriceInfo.priceTypes.INSTABUY) {
            if(priceInfo.getPrice()-maximumRounding > priceInfo.getMarketPrice())
                return statuses.OUTDATED;
        } else {
            if(priceInfo.getPrice()+maximumRounding < priceInfo.getMarketPrice())
                return statuses.OUTDATED;
        }
        return statuses.COMPETITIVE;
    }

    public double getFlipPrice(){
        updateMarketPrice();
        if(priceInfo.getMarketOppositePrice() == 0)
            return 0;
        if (priceInfo.getPriceType() == OrderPriceInfo.priceTypes.INSTABUY) {
            return (priceInfo.getMarketOppositePrice() + .1);
        } else {
            return (priceInfo.getMarketOppositePrice() - .1);
        }
    }

    public void setFilled(){
        amountFilled = volume;
        fillStatus = statuses.FILLED;
    }

    public void removeFromWatchedItems(){
        if(!BUConfig.get().watchedOrders.remove(this))
            Util.notifyAll("Error removing " + name + " from watched items. Item couldn't be found.");
        BUConfig.HANDLER.save();
        OrderData.updateOutdatedItems();
    }
}
