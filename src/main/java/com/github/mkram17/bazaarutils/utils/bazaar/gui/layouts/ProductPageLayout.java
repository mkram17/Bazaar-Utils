package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenSwitch;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Layout utilities for the Bazaar item page.
 *
 * <p>Handles slot queries, display-product resolution, and lore amount extraction.
 */
public final class ProductPageLayout {

    private ProductPageLayout() {}


    public static Optional<ItemInfo> getCreateBuyOrderItem(@NotNull ScreenContext context) {
        return ScreenSwitch.<Optional<ItemInfo>>on(context)
                .when(BazaarScreenType.PRODUCT_PAGE, ctx -> getSlot(ctx, BazaarSlots.PRODUCT_PAGE.CREATE_BUY_ORDER.slot))
                .orElse(Optional.empty());
    }

    public static Optional<ItemInfo> getCreateSellOfferItem(@NotNull ScreenContext context) {
        return ScreenSwitch.<Optional<ItemInfo>>on(context)
                .when(BazaarScreenType.PRODUCT_PAGE, ctx -> getSlot(ctx, BazaarSlots.PRODUCT_PAGE.CREATE_SELL_OFFER.slot))
                .orElse(Optional.empty());
    }

    public static Optional<ItemInfo> getDisplayItem(@NotNull ScreenContext context) {
        return ScreenSwitch.<Optional<ItemInfo>>on(context)
                .when(BazaarScreenType.PRODUCT_PAGE, ctx -> getSlot(ctx, BazaarSlots.PRODUCT_PAGE.ITEM_DISPLAY.slot))
                .orElse(Optional.empty());
    }

    public static Optional<String> getDisplayItemName(@NotNull ScreenContext context) {
        return getDisplayItem(context)
                .map(ItemInfo::itemStack)
                .map(ItemStack::getCustomName)
                .map(Component::getString);
    }

    public static Optional<String> getDisplayProductInfo(@NotNull ScreenContext context) {
        return getDisplayItemName(context).flatMap(BazaarDataUtil::findProductIdOptional);
    }

    private static Optional<ItemInfo> getSlot(
            @NotNull ScreenContext context,
            BazaarSlots.BazaarSlot slot) {
        return context.as(ContainerScreen.class).flatMap(screen -> SlotLookup.getInventoryItem(screen.getMenu().getContainer(), slot));
    }
}