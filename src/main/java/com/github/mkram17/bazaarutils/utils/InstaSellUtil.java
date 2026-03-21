package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.TextSearch;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.*;

//NOTE FROM INSTASELLRESTRICTION: if there are items with no buy orders in inv, you get "Some items can't be sold" and there are 2 extra components

public class InstaSellUtil {
    public static List<OrderInfo> getInstaSellOrders(List<ItemStack> itemStacks) {
        if (!ScreenManager.getInstance().isCurrent(BazaarScreens.MAIN_PAGE)) {
            return Collections.emptyList();
        }

        Optional<ItemStack> instaSellItemStack = getInstaSellItemStack(itemStacks);

        if (instaSellItemStack.isEmpty()) {
            Util.notifyError("Could not find insta-sell item stack in Bazaar GUI. Please report this issue.", new Throwable());

            return Collections.emptyList();
        }

        return getInstaSellOrderData(instaSellItemStack.get());
    }

    public static Optional<ItemStack> getInstaSellItemStack(List<ItemStack> itemStacks) {
        return itemStacks.stream().filter(itemStack -> itemStack.getName().getString().contains("Sell Inventory Now")).findFirst();
    }

    private static List<OrderInfo> getInstaSellOrderData(ItemStack instaSellItemStack) {
        List<OrderInfo> orderData = new ArrayList<>();

        LoreComponent loreComponents = instaSellItemStack.get(DataComponentTypes.LORE);

        if (loreComponents == null) {
            return Collections.emptyList();
        }

        List<Text> loreLines = loreComponents.lines();

        return findInstaSellOrderData(loreLines);
    }

    private static List<OrderInfo> findInstaSellOrderData(List<Text> loreLines) {
        List<OrderInfo> orderData = new ArrayList<>();

        List<Text> itemLoreLines = getItemLoreLines(loreLines);

        for (Text line : itemLoreLines) {
            int volume = getVolume(line);

            double totalPrice = getTotalPrice(line);
            double pricePerUnit = Math.round(totalPrice / volume * 10)/10.0;

            String name = getName(line);

            OrderInfo buyOrderItem = new OrderInfo(name, TransactionType.Side.BUY, null, volume, pricePerUnit, null);

            orderData.add(buyOrderItem);
        }

        return orderData;
    }

    private static List<Text> getItemLoreLines(List<Text> loreLines) {
        int firstItemIndex = TextSearch.indexOf(loreLines, "coins");
        int totalCoinIndex = TextSearch.lastIndexOf(loreLines, "coins");

        if (firstItemIndex == -1 || totalCoinIndex == -1) {
            return Collections.emptyList();
        }

        int lastItemIndex = TextSearch.lastIndexOf(loreLines.subList(0, totalCoinIndex-1), "coins");

        return loreLines.subList(firstItemIndex, lastItemIndex+1);
    }

    private static int getVolume(Text text) {
        String volumeString = text.getSiblings().get(1).getString();

        return Util.parseNumber(volumeString);
    }
    private static String getName(Text text) {
        String nameString = text.getSiblings().get(3).getString();

        return nameString.trim();
    }
    private static double getTotalPrice(Text text) {
        String priceString = text.getSiblings().get(5).getString();

        return Util.parseNumber(priceString);
    }
}