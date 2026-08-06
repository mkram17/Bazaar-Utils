package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenSwitch;
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

    private static final ScreenMatcher<BazaarScreenType> OVERVIEW_SCREENS = BazaarScreenMatcher.of(BazaarScreenType.MAIN_PAGE, BazaarScreenType.SEARCH_PAGE);

    public static Optional<ItemInfo> getInstantSellItem(@NotNull ScreenContext context) {
        return ScreenSwitch.<Optional<ItemInfo>>on(context)
                .when(OVERVIEW_SCREENS, ctx -> SlotLookup.getInventoryItem(ctx, BazaarSlots.OVERVIEW_PAGE.SELL_INVENTORY.slot))
                .when(BazaarScreenType.PRODUCT_PAGE, ctx -> SlotLookup.getInventoryItem(ctx, BazaarSlots.PRODUCT_PAGE.SELL_INSTANTLY.slot))
                .when(BazaarScreenType.PRODUCTS_CATALOG_PAGE, ctx -> SlotLookup.getInventoryItem(ctx, BazaarSlots.PRODUCTS_CATALOG_PAGE.SELL_INVENTORY.slot))
                .orElse(Optional.empty());
    }

    public static Optional<ItemInfo> getSellSacksItem(@NotNull ScreenContext context) {
        return ScreenSwitch.<Optional<ItemInfo>>on(context)
                .when(OVERVIEW_SCREENS, ctx -> SlotLookup.getInventoryItem(ctx, BazaarSlots.OVERVIEW_PAGE.SELL_SACKS.slot))
                .when(BazaarScreenType.PRODUCT_PAGE, ctx -> SlotLookup.getInventoryItem(ctx, BazaarSlots.PRODUCT_PAGE.SELL_SACKS.slot))
                .when(BazaarScreenType.PRODUCTS_CATALOG_PAGE, ctx -> SlotLookup.getInventoryItem(ctx, BazaarSlots.PRODUCTS_CATALOG_PAGE.SELL_SACKS.slot))
                .orElse(Optional.empty());
    }
}
