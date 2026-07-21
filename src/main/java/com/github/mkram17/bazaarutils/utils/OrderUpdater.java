package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.misc.orderinfo.ItemInfo;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public class OrderUpdater {
    private static Container lowerChestInventory;

    private static final String PREFIX_BUY = "BUY";
    private static final String PREFIX_SELL = "SELL";

    private static final String LORE_FILLED = "Filled";
    private static final String LORE_PER_UNIT = "per unit";
    private static final String LORE_TO_CLAIM = "to claim!";
    private static final String LORE_ITEMS = "items";
    private static final String LORE_COINS = " coins";
    private static final String LORE_ORDER_AMOUNT = "Order amount: ";
    private static final String LORE_OFFER_AMOUNT = "Offer amount: ";
    private static final String WORD_UNIT = "unit:";
    private static final double FILL_TOLERANCE_RATIO = 0.05; //5%

    @EventHandler(priority = EventPriority.HIGH)
    public static void onGUI(ChestLoadedEvent event) {
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        if (!screenInfo.inMenu(ScreenInfo.BazaarMenuType.ORDER_SCREEN)) return;

        lowerChestInventory = event.getLowerChestInventory();
        List<ItemStack> allInventoryStacks = event.getItemStacks();
        List<ItemStack> orderStacks = extractOrderStacks(allInventoryStacks);
        updateWatchedOrders(orderStacks);
    }

    private static void updateWatchedOrders(List<ItemStack> orderStacks) {
        List<OrderInfoContainer> parsedOrders = orderStacks.stream()
                .map(OrderUpdater::parseOrderFromItemStack)
                .toList();
        updateOrders(parsedOrders);
    }

    private static void updateOrders(List<OrderInfoContainer> parsedOrders) {
        List<BazaarOrder> userOrdersCopy = new ArrayList<>(BUConfig.get().userOrders);

        parsedOrders.iterator().forEachRemaining(order -> {
            Optional<BazaarOrder> matchedOrder = order.findOrderInList(userOrdersCopy);

            //if we find a match, update its values that can be found only in the orders menu
            matchedOrder.ifPresent(matched -> {
                updateBazaarOrder(matched, order.getItemInfo());
                userOrdersCopy.remove(matched);
            });

            //if we can't find a match, this is an order that isn't being tracked, so we add it (shouldn't happen)
            if(matchedOrder.isEmpty()){
                BazaarOrder newOrder =  order.toBazaarOrder();
                Util.addWatchedOrder(newOrder);
                //add item info, amount filled, amount claimed
                updateBazaarOrder(newOrder, order.getItemInfo());
            }
        });

        //any orders left in userOrdersCopy are old orders that should be removed
        if(!userOrdersCopy.isEmpty()){
            userOrdersCopy.forEach(BazaarOrder::removeFromWatchedItems);
        }
    }

    private static void updateBazaarOrder(BazaarOrder order, ItemInfo parsedItemInfo) {
        if (parsedItemInfo == null){
            Util.notifyError("Error while updating order info", new Throwable("ItemInfo is null"));
            return;
        }
        order.setItemInfo(parsedItemInfo);

        ItemLore loreComponent = order.getItemInfo().itemStack().get(DataComponents.LORE);
        if (loreComponent == null) {
            return;
        }

        List<Component> loreLines = loreComponent.styledLines();


        int amountFilled = parseAmountFilled(loreLines);
        int amountClaimed = parseAmountClaimed(loreLines, amountFilled);
        double pricePerItem = parseUnitPrice(loreLines);

        int volume = order.getVolume();
        order.setAmountFilled(amountFilled);
        if (Util.genericIsSimilarValue(amountFilled, volume, volume * FILL_TOLERANCE_RATIO)) {
            order.setFilled();
        }

        if (amountClaimed >= 0) {
            order.setAmountClaimed(amountClaimed);
        }
        order.setPricePerItem(pricePerItem);
        order.setTolerance(0.0);

    }

    private static OrderInfoContainer parseOrderFromItemStack(ItemStack stack) {
        String title = stack.getHoverName().getString();
        ItemInfo itemInfo = new ItemInfo(mapScreenIndexToInventoryIndex(stack), stack);

        ItemLore loreComponent = stack.get(DataComponents.LORE);
        if (loreComponent == null) {
            return null;
        }
        List<Component> loreLines = loreComponent.styledLines();

        OrderType orderType = detectOrderType(title);
        if (orderType == null) {
            Util.notifyError("Error while parsing order from item stack", new Exception("Could not determine order side"));
            return null;
        }

        double unitPrice = parseUnitPrice(loreLines);
        if (Double.isNaN(unitPrice)) {
            Util.notifyError("Error while parsing order from item stack", new Exception("Missing unit price"));
            return null;
        }

        int volume = parseVolume(loreLines);
        if (volume == -1) {
            Util.notifyError("Error while parsing order from item stack", new Exception("Missing volume"));
            return null;
        }

        String cleanName = stripPrefix(title, orderType);

        PriceInfoContainer.PriceType priceType = orderType == OrderType.SELL
                ? PriceInfoContainer.PriceType.INSTABUY
                : PriceInfoContainer.PriceType.INSTASELL;

        return new OrderInfoContainer(cleanName, volume, unitPrice, priceType, itemInfo);
    }

    private static OrderType detectOrderType(String title) {
        if (title.contains(PREFIX_BUY)) return OrderType.BUY;
        if (title.contains(PREFIX_SELL)) return OrderType.SELL;
        return null;
    }

    private static String stripPrefix(String title, OrderType type) {
        String prefix = (type == OrderType.BUY ? PREFIX_BUY : PREFIX_SELL) + " ";
        return title.startsWith(prefix) ? title.substring(prefix.length()) : title;
    }

    private static double parseUnitPrice(List<Component> lore) {
        Component line = Util.findComponentWith(lore, LORE_PER_UNIT);
        if (line == null) return Double.NaN;
        String raw = line.getString();
        try {
            return Double.parseDouble(Util.extractTextAfterWord(raw, WORD_UNIT));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private static int parseVolume(List<Component> lore) {
        Component line = Util.findComponentWith(lore, LORE_ORDER_AMOUNT);
        if (line == null) {
            line = Util.findComponentWith(lore, LORE_OFFER_AMOUNT);
        }
        if (line == null) return -1;
        try {
            // Original logic used sibling index 1
            return Util.parseNumber(line.getSiblings().get(1).getString());
        } catch (Exception e) {
            return -1;
        }
    }

    private static int parseAmountFilled(List<Component> lore) {
        Component filledLine = Util.findComponentWith(lore, LORE_FILLED);
        if (filledLine == null) return -1;
        String s = filledLine.getString();
        int slash = s.indexOf('/');
        if (slash == -1) return -1;
        try {
            // Original substring(8, indexOf("/")) behavior retained
            return Util.parseNumber(s.substring(8, slash));
        } catch (Exception e) {
            return -1;
        }
    }

    private static int parseAmountClaimed(List<Component> lore, int amountFilled) {
        if (amountFilled < 0) return -1;
        Component unclaimedLine = Util.findComponentWith(lore, LORE_TO_CLAIM);
        if (unclaimedLine == null) {
            return amountFilled; // fully claimed
        }
        String raw = unclaimedLine.getString();
        int start = 9; // preserve original logic substring(9, ...)
        int end;
        if (!raw.contains(LORE_ITEMS)) {
            end = raw.indexOf(LORE_COINS);
        } else {
            end = raw.indexOf(LORE_ITEMS) - 1;
        }
        if (end <= start) return -1;
        try {
            int unclaimed = Util.parseNumber(raw.substring(start, end));
            return amountFilled - unclaimed;
        } catch (Exception e) {
            return -1;
        }
    }

    private static List<ItemStack> extractOrderStacks(List<ItemStack> screenStacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : screenStacks) {
            if (stack.is(VersionCompat.stainedGlassPane(DyeColor.BLACK))) continue;
            if (stack.is(Items.ARROW)) break; // stop at navigation arrow
            result.add(stack);
        }
        return result;
    }

    private static int mapScreenIndexToInventoryIndex(ItemStack target) {
        if (lowerChestInventory == null) return -1;
        for (int i = 0; i < lowerChestInventory.getContainerSize(); i++) {
            ItemStack current = lowerChestInventory.getItem(i);
            if (!current.isEmpty() && current.equals(target)) {
                return i;
            }
        }
        return -1;
    }

    @RunOnInit
    public static void subscribe() {
        EVENT_BUS.subscribe(OrderUpdater.class);
    }

    private enum OrderType {
        BUY, SELL
    }
}