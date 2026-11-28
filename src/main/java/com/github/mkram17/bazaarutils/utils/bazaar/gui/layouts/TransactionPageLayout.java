package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenSwitch;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Layout utilities for Bazaar transaction confirmation, option, and sign-input screens.
 */
public final class TransactionPageLayout {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("Amount: (?<amount>[0-9,.]+)x");
    private static final Pattern PURCHASE_LIMIT_PATTERN = Pattern.compile("Buy up to (?<amount>[0-9,.]+)x\\.");

    private TransactionPageLayout() {}

    public static Optional<ItemInfo> getConfirmSellOfferItem(@NotNull ScreenContext context) {
        return ScreenSwitch.<Optional<ItemInfo>>on(context)
                .when(BazaarScreenType.SELL_OFFER_CONFIRMATION, ctx -> getSlot(ctx, BazaarSlots.SELL_OFFER.CONFIRM_SELL_OFFER.slot))
                .orElse(Optional.empty());
    }

    public static Optional<ItemInfo> getConfirmBuyOrderItem(@NotNull ScreenContext context) {
        return ScreenSwitch.<Optional<ItemInfo>>on(context)
                .when(BazaarScreenType.BUY_ORDER_CONFIRMATION, ctx -> getSlot(ctx, BazaarSlots.BUY_ORDER.CONFIRM_BUY_ORDER.slot))
                .orElse(Optional.empty());
    }

    public static Optional<String[]> getFlipSignLines(@Nullable ScreenContext context) {
        if (context == null) return Optional.empty();

        return ScreenSwitch.<Optional<String[]>>on(context)
                .when(BazaarScreenType.COMPLETED_BUY_ORDER_FLIP_PRICE_INPUT, ctx ->
                        Optional.ofNullable(((AccessorSignEditScreen) ctx.screen()).getMessages())
                )
                .orElse(Optional.empty());
    }

    public static Optional<Double> findOptionAmount(ItemStack option) {
        String name = option.getCustomName() != null ? option.getCustomName().getString() : option.getHoverName().getString();
        return LoreParser.matchDouble(option, AMOUNT_PATTERN, "amount", "option amount on " + name);
    }

    public static Optional<Integer> findBuyOrderAmountLimit(ItemStack inputSign) {
        String name = inputSign.getCustomName() != null ? inputSign.getCustomName().getString() : inputSign.getHoverName().getString();
        return LoreParser.matchInt(inputSign, PURCHASE_LIMIT_PATTERN, "amount", "buy order limit on " + name);
    }

    private static Optional<ItemInfo> getSlot(
            @NotNull ScreenContext context,
            BazaarSlots.BazaarSlot slot) {
        return context.as(ContainerScreen.class).flatMap(screen -> SlotLookup.getInventoryItem(screen.getMenu().getContainer(), slot));
    }
}
