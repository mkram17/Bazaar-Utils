package com.github.mkram17.bazaarutils.utils.bazaar.gui;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class BazaarScreenHandler {
    public static final Pattern AMOUNT_PATTERN         = Pattern.compile("Amount: (?<amount>[0-9,.]+)x");
    public static final Pattern SELL_LIMIT_PATTERN     = Pattern.compile("Inventory: (?<amount>[0-9,.]+) items");
    public static final Pattern PURCHASE_LIMIT_PATTERN = Pattern.compile("Buy up to (?<amount>[0-9,.]+)x.");

    private BazaarScreenHandler() {}

    public static Optional<ItemInfo> getInstantSellItem(@NotNull ScreenContext context) {
        if (context.isAnyOf(BazaarScreens.MAIN_PAGE))
            return getItemFromSlot(context, BazaarSlots.OVERVIEW_PAGE.SELL_INVENTORY.slot);

        if (context.isAnyOf(BazaarScreens.ITEM_PAGE))
            return getItemFromSlot(context, BazaarSlots.ITEM_PAGE.SELL_INSTANTLY.slot);

        if (context.isAnyOf(BazaarScreens.ITEMS_GROUP_PAGE))
            return getItemFromSlot(context, BazaarSlots.ITEMS_GROUP_PAGE.SELL_INVENTORY.slot);

        return Optional.empty();
    }

    public static Optional<ItemInfo> getSellSacksItem(@NotNull ScreenContext context) {
        if (context.isAnyOf(BazaarScreens.MAIN_PAGE))
            return getItemFromSlot(context, BazaarSlots.OVERVIEW_PAGE.SELL_SACKS.slot);

        if (context.isAnyOf(BazaarScreens.ITEM_PAGE))
            return getItemFromSlot(context, BazaarSlots.ITEM_PAGE.SELL_SACKS.slot);

        if (context.isAnyOf(BazaarScreens.ITEMS_GROUP_PAGE))
            return getItemFromSlot(context, BazaarSlots.ITEMS_GROUP_PAGE.SELL_SACKS.slot);

        return Optional.empty();
    }

    public static Optional<ItemInfo> getDisplayItem(@NotNull ScreenContext context) {
        // #isAnyOf rather than #matches — likely to hit computation cache from the
        // preceding isCurrent call in the same stack.
        if (!context.isAnyOf(BazaarScreens.ITEM_PAGE)) return Optional.empty();

        return getItemFromSlot(context, BazaarSlots.ITEM_PAGE.ITEM_DISPLAY.slot);
    }

    public static Optional<String> getDisplayItemName(@NotNull ScreenContext context) {
        return getDisplayItem(context)
                .map(ItemInfo::itemStack)
                .map(ItemStack::getCustomName)
                .map(Component::getString);
    }

    public static Optional<String> getDisplayProductId(@NotNull ScreenContext context) {
        return getDisplayItemName(context)
                .flatMap(BazaarDataUtil::findProductIdOptional);
    }

    public static Optional<OrderInfo> getDisplayOrderInfo(@NotNull ScreenContext context) {
        return getDisplayItemName(context)
                .map(name -> new OrderInfo(name, TransactionType.Side.SELL, null, null, null, null));
    }

    public static String getItemNameFromTitle() {
        String containerName = ContainerManager.getContainerName();

        if (ScreenManager.getInstance().isCurrent(BazaarScreens.INSTANT_BUY)) {
            return containerName.substring(0, containerName.indexOf("➜") - 1);
        }

        return containerName.substring(containerName.indexOf("➜") + 2);
    }

    public static String getItemName(List<ItemStack> containerItems) {
        String nameFromTitle = getItemNameFromTitle();
        if (!OrderInfo.isValidName(nameFromTitle) || nameFromTitle.length() >= 30) {
            return getItemNameFromStacks(containerItems, nameFromTitle);
        }
        return nameFromTitle;
    }

    private static String getItemNameFromStacks(List<ItemStack> stacks, String nameFromTitle) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getHoverName().getString().startsWith(nameFromTitle)) {
                return stack.getCustomName().getString();
            }
        }
        return "???";
    }

    private static Optional<ItemInfo> getItemFromSlot(@NotNull ScreenContext context, BazaarSlots.BazaarSlot slot) {
        return context.as(ContainerScreen.class)
                .map(screen -> SlotLookup.getInventoryItem(screen.getMenu().getContainer(), slot));
    }

    public static Optional<Double> findOptionAmount(ItemStack option) {
        return LoreParser.matchDouble(option, AMOUNT_PATTERN, "amount", "option amount on " + option.getCustomName());
    }

    public static Optional<Integer> findBuyOrderAmountLimit(ItemStack inputSign) {
        return LoreParser.matchInt(inputSign, PURCHASE_LIMIT_PATTERN, "amount", "buy order limit on " + inputSign.getCustomName());
    }

    public static Optional<Integer> findSellAmountLimit(ItemStack inputSign) {
        return LoreParser.matchInt(inputSign, SELL_LIMIT_PATTERN, "amount", "sell limit on " + inputSign.getCustomName());
    }
}