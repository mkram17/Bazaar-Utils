package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Layout utilities for the Bazaar item page.
 *
 * <p>Handles slot queries, display-item resolution, and lore amount extraction
 * for the BUY/SELL order creation flow.
 */
public final class ItemPageLayout {

    private ItemPageLayout() {}

    // ── Slot queries ──────────────────────────────────────────────────────────

    public static Optional<ItemInfo> getCreateBuyOrderItem(@NotNull ScreenContext context) {
        return getIf(context, BazaarScreenType.ITEM_PAGE, BazaarSlots.ITEM_PAGE.CREATE_BUY_ORDER.slot);
    }

    public static Optional<ItemInfo> getCreateSellOfferItem(@NotNull ScreenContext context) {
        return getIf(context, BazaarScreenType.ITEM_PAGE, BazaarSlots.ITEM_PAGE.CREATE_SELL_OFFER.slot);
    }

    public static Optional<ItemInfo> getDisplayItem(@NotNull ScreenContext context) {
        return getIf(context, BazaarScreenType.ITEM_PAGE, BazaarSlots.ITEM_PAGE.ITEM_DISPLAY.slot);
    }

    public static Optional<String> getDisplayItemName(@NotNull ScreenContext context) {
        return getDisplayItem(context)
                .map(ItemInfo::itemStack)
                .map(ItemStack::getCustomName)
                .map(Component::getString);
    }

    public static Optional<ProductInfo> getDisplayProductInfo(@NotNull ScreenContext context) {
        return getDisplayItemName(context)
                .flatMap(ProductInfo::fromDisplayName);
    }

    private static Optional<ItemInfo> getIf(
            @NotNull ScreenContext context,
            BazaarScreenType type,
            BazaarSlots.BazaarSlot slot) {
        if (!context.isAnyOf(type)) return Optional.empty();

        return context.as(ContainerScreen.class)
                .map(screen -> SlotLookup.getInventoryItem(screen.getMenu().getContainer(), slot));
    }
}