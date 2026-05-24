package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Layout utilities for the Bazaar overview/group pages.
 *
 * <p>Several actions (instant sell, sell sacks) are reachable from multiple
 * screen types. This class centralises the dispatch so call sites don't need
 * to know which slot the action lives on per screen.
 */
public final class SellablePageLayout {

    private SellablePageLayout() {}

    public static Optional<ItemInfo> getInstantSellItem(@NotNull ScreenContext context) {
        if (context.is(BazaarScreenType.MAIN_PAGE) || context.is(BazaarScreenType.SEARCH_PAGE))
            return getSlot(context, BazaarSlots.OVERVIEW_PAGE.SELL_INVENTORY.slot);

        if (context.is(BazaarScreenType.PRODUCT_PAGE))
            return getSlot(context, BazaarSlots.PRODUCT_PAGE.SELL_INSTANTLY.slot);

        if (context.is(BazaarScreenType.PRODUCTS_CATALOG_PAGE))
            return getSlot(context, BazaarSlots.PRODUCTS_CATALOG_PAGE.SELL_INVENTORY.slot);

        return Optional.empty();
    }

    public static Optional<ItemInfo> getSellSacksItem(@NotNull ScreenContext context) {
        if (context.is(BazaarScreenType.MAIN_PAGE) || context.is(BazaarScreenType.SEARCH_PAGE))
            return getSlot(context, BazaarSlots.OVERVIEW_PAGE.SELL_SACKS.slot);

        if (context.is(BazaarScreenType.PRODUCT_PAGE))
            return getSlot(context, BazaarSlots.PRODUCT_PAGE.SELL_SACKS.slot);

        if (context.is(BazaarScreenType.PRODUCTS_CATALOG_PAGE))
            return getSlot(context, BazaarSlots.PRODUCTS_CATALOG_PAGE.SELL_SACKS.slot);

        return Optional.empty();
    }

    private static Optional<ItemInfo> getSlot(
            @NotNull ScreenContext context,
            BazaarSlots.BazaarSlot slot) {
        return context.as(ContainerScreen.class).flatMap(screen -> SlotLookup.getInventoryItem(screen.getMenu().getContainer(), slot));
    }
}