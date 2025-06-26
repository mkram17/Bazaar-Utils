package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.features.OrderStatusHighlight;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderPriceInfo;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static com.github.mkram17.bazaarutils.BazaarUtils.eventBus;

public class ItemUpdater implements BUListener {
    private static ArrayList<ItemStack> orderStacks = new ArrayList<>();
    private static List<ItemStack> orderScreen;
    private static Inventory lowerChestInventory;

    @EventHandler(priority = EventPriority.HIGH)
    public static void onGUI(ChestLoadedEvent e){
        if(!GUIUtils.inOrderScreen())
            return;

        lowerChestInventory = e.getLowerChestInventory();
        orderScreen = e.getItemStacks();
        orderStacks = findOrders(orderScreen);
        updateWatchedItems(orderStacks);
    }

    private static void updateWatchedItems(ArrayList<ItemStack> orderStacks){
        List<OrderData> foundItems = new ArrayList<>();
        for(ItemStack stack : orderStacks){
            //? if >= 1.21.4 {
            String customName = stack.getCustomName().getString();
            //?} else {
            /*String customName = stack.getComponentChanges().get(DataComponentTypes.CUSTOM_NAME).get().getString();
            *///?}
            String name = "";
            boolean isSellOrder;
            double unitPrice;
            double fullPrice;
            int volumeFilled = -1;
            int totalVolume;
            int amountUnclaimed;
            int amountClaimed = -1;

            if(stack.getComponentChanges().get(DataComponentTypes.LORE) == null)
                continue;
            List<Text> changedComponents = stack.getComponentChanges().get(DataComponentTypes.LORE).get().styledLines();

            if(customName.contains("BUY")){
                name = customName.substring(4);
                isSellOrder = false;
            } else {
                name = customName.substring(5);
                isSellOrder = true;
            }

            if(Util.findComponentWith(changedComponents, "Filled") != null) {
                var volumeFilledString = Util.findComponentWith(changedComponents, "Filled");
                volumeFilled = Util.parseNumber(volumeFilledString.substring(8, volumeFilledString.indexOf("/")));
                totalVolume = Util.parseNumber(volumeFilledString.substring(volumeFilledString.indexOf("/") + 1, volumeFilledString.lastIndexOf(" ")));
            } else {
//                fullPrice = Util.parseNumber(Util.extractTextAfterWord(Util.findComponentWith(changedComponents, "Worth"), "Worth"));
                totalVolume = Util.parseNumber(changedComponents.get(2).getSiblings().get(1).getString());

            }
            unitPrice = Double.parseDouble(Util.extractTextAfterWord(Util.findComponentWith(changedComponents, "per unit"), "unit:"));
            fullPrice = unitPrice*totalVolume;

            if(volumeFilled != -1) {
                if (Util.findComponentWith(changedComponents, "to claim!") != null) {
                    var amountUnclaimedString = Util.findComponentWith(changedComponents, "to claim!");
                    if(!amountUnclaimedString.contains("items")) {
                        amountUnclaimed = Util.parseNumber(amountUnclaimedString.substring(9, amountUnclaimedString.indexOf(" coins")));
                    } else {
                        amountUnclaimed = Util.parseNumber(amountUnclaimedString.substring(9, amountUnclaimedString.indexOf("items") - 1));
                    }
                    amountClaimed = volumeFilled - amountUnclaimed;
                } else {
                    amountClaimed = volumeFilled;
                }
            }

            OrderData tempItem = isSellOrder ? new OrderData(name, fullPrice, OrderPriceInfo.priceTypes.INSTABUY, totalVolume) : new OrderData(name, fullPrice, OrderPriceInfo.priceTypes.INSTASELL, totalVolume);
            tempItem.setMaximumRounding(0.0);

            if(volumeFilled > -1) {
                tempItem.setAmountFilled(volumeFilled);
                if (volumeFilled == totalVolume)
                    tempItem.setFilled();
            }
            if(amountClaimed > -1)
                tempItem.setAmountClaimed(amountClaimed);

            //if updateWithItem() returns null addWatchedItem returns, so it is only called when no match is found
            foundItems.add(tempItem);
            Util.addWatchedItem(updateWithItem(tempItem));

            if(tempItem.getOutdatedStatus() == OrderData.statuses.OUTDATED ||
               tempItem.getOutdatedStatus() == OrderData.statuses.COMPETITIVE ||
               tempItem.getOutdatedStatus() == OrderData.statuses.MATCHED ||
               tempItem.getFillStatus() == OrderData.statuses.FILLED) {
                OrderStatusHighlight.addHighlightedOrder(mapScreenIndexToInventoryIndex(stack), tempItem);
            }

//            if(tempItem.getOutdatedStatus() == OrderData.statuses.OUTDATED)
//                OrderStatusHighlight.addOutdatedSlotIndex(mapScreenIndexToInventoryIndex(stack));
//            else if(tempItem.getOutdatedStatus() == OrderData.statuses.COMPETITIVE)
//                OrderStatusHighlight.addCompetitiveSlotIndex(mapScreenIndexToInventoryIndex(stack));
//            else if(tempItem.getOutdatedStatus() == OrderData.statuses.MATCHED)
//                OrderStatusHighlight.addMatchedSlotIndex(mapScreenIndexToInventoryIndex(stack));
        }
        removeOldItems(foundItems);
        OrderData.updateOutdatedItems();
    }

    private static int mapScreenIndexToInventoryIndex(ItemStack stack) {
        if (lowerChestInventory == null) {
            return -1;
        }
        for (int i = 0; i < lowerChestInventory.size(); i++) {
            ItemStack inventoryStack = lowerChestInventory.getStack(i);
            if (!inventoryStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, inventoryStack)) {
                return i;
            }
        }
        return -1;
    }

    private static void removeOldItems(List<OrderData> foundItems){
        List<OrderData> itemsToRemove = new ArrayList<>();

        for(OrderData item : BUConfig.get().watchedOrders){
            if(OrderData.findItem(item, foundItems) == null) {
                itemsToRemove.add(item);
            }
        }

        for(OrderData item : itemsToRemove) {
            item.removeFromWatchedItems();
            Util.notifyAll("Removed " + item.getGeneralInfo() + " from watched items.", Util.notificationTypes.ITEMDATA);
        }

        BUConfig.HANDLER.save();
    }

    private static OrderData updateWithItem(OrderData foundItem){
        OrderData match = OrderData.findItem(foundItem, BUConfig.get().watchedOrders);
        if(match == null) {
            Util.notifyAll("No match found", Util.notificationTypes.ITEMDATA);
            return foundItem;
        }

        if (match.getMaximumRounding() != 0.0) {
            Util.notifyAll("Updating maximum rounding of " + match.getName() + " from " + match.getMaximumRounding() + " to 0.0 . Price: " + foundItem.getPriceInfo().getPrice(), Util.notificationTypes.ITEMDATA);
            match.setMaximumRounding(0.0);
        }
//        Util.notifyAll("Match found", Util.notificationTypes.ITEMDATA);
        if(match.getPriceInfo().getPrice() != foundItem.getPriceInfo().getPrice()){
            Util.notifyAll("Updating price of " + match.getName() + " from " + match.getPriceInfo().getPrice() + " to " + foundItem.getPriceInfo().getPrice(), Util.notificationTypes.ITEMDATA);
            match.getPriceInfo().setPrice(foundItem.getPriceInfo().getPrice());
        }
        if(match.getFillStatus() != foundItem.getFillStatus()){
            Util.notifyAll("Updating status of " + match.getName() + " from " + match.getFillStatus() + " to " + foundItem.getFillStatus(), Util.notificationTypes.ITEMDATA);
            match.setFillStatus(foundItem.getFillStatus());
        }
        if(match.getAmountFilled() != foundItem.getAmountFilled()){
            Util.notifyAll("Updating volume filled of " + match.getName() + " from " + match.getAmountFilled() + " to " + foundItem.getAmountFilled(), Util.notificationTypes.ITEMDATA);
            match.setAmountFilled(foundItem.getAmountFilled());
        }
        if(match.getAmountClaimed() != foundItem.getAmountClaimed() && foundItem.getAmountClaimed() >= 0){
            Util.notifyAll("Updating amount claimed of " + match.getName() + " from " + match.getAmountClaimed() + " to " + foundItem.getAmountClaimed(), Util.notificationTypes.ITEMDATA);
            match.setAmountClaimed(foundItem.getAmountClaimed());
        }
        BUConfig.HANDLER.save();
        return null;
    }

    //TODO  low priority -- there is definitely a better way to do this
    private static ArrayList<ItemStack> findOrders(List<ItemStack> orderScreenStacks){
            ArrayList<ItemStack> items = new ArrayList<>();
            int lastBlackPaneIndex = -1, firstAfterIndex = -1;

            for (int i = 0; i < orderScreenStacks.size(); i++) {
                if (orderScreenStacks.get(i).isOf(Items.BLACK_STAINED_GLASS_PANE)) {
                    lastBlackPaneIndex = i;
                } else{
                    break;
                }
            }

            for (int i = lastBlackPaneIndex + 1; i < orderScreenStacks.size() - 2; i++) {
                if (orderScreenStacks.get(i).isOf(Items.BLACK_STAINED_GLASS_PANE) && orderScreenStacks.get(i+1).isOf(Items.BLACK_STAINED_GLASS_PANE) && orderScreenStacks.get(i+2).isOf(Items.BLACK_STAINED_GLASS_PANE)) {
                    firstAfterIndex = i;
                    break;
                }
            }

            if (firstAfterIndex == -1)
                return items;

            for (int i = lastBlackPaneIndex + 1; i < firstAfterIndex; i++) {
                if(!orderScreenStacks.get(i).isOf(Items.BLACK_STAINED_GLASS_PANE))
                    items.add(orderScreenStacks.get(i));
            }

            //if there are no items last index+1 will be arrow, so in that case it should return empty list
            if(orderScreenStacks.get(lastBlackPaneIndex+1).isOf(Items.ARROW))
                return new ArrayList<>();

            return items;
    }

    @Override
    public void subscribe() {
        eventBus.subscribe(this);
    }
}
