package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Layout utilities for the Bazaar item page.
 */
public final class TransactionPageLayout {

    public static final Pattern AMOUNT_PATTERN = Pattern.compile("Amount: (?<amount>[0-9,.]+)x");
    public static final Pattern SELL_LIMIT_PATTERN = Pattern.compile("Inventory: (?<amount>[0-9,.]+) items");
    public static final Pattern PURCHASE_LIMIT_PATTERN = Pattern.compile("Buy up to (?<amount>[0-9,.]+)x.");

    private TransactionPageLayout() {}

    public static Optional<ItemInfo> getConfirmSellOfferItem(@NotNull ScreenContext context) {
        return getIf(context, BazaarScreenType.BUY_ORDER_CONFIRMATION, BazaarSlots.SELL_OFFER.CONFIRM_SELL_OFFER.slot);
    }

    public static Optional<ItemInfo> getConfirmBuyOrderItem(@NotNull ScreenContext context) {
        return getIf(context, BazaarScreenType.BUY_ORDER_CONFIRMATION, BazaarSlots.BUY_ORDER.CONFIRM_BUY_ORDER.slot);
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

    private static Optional<ItemInfo> getIf(
            @NotNull ScreenContext context,
            ScreenType type,
            BazaarSlots.BazaarSlot slot) {
        if (!context.is(type)) return Optional.empty();

        return context.as(ContainerScreen.class).flatMap(screen -> SlotLookup.getInventoryItem(screen.getMenu().getContainer(), slot));
    }
}