package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import net.minecraft.world.item.ItemStack;

/**
 * @see com.github.mkram17.bazaarutils.data.SellableAPI
 */
public final class SellSacksParser {
    private SellSacksParser() {}

    /**
     * Parses the sell-sacks button present on catalog/overview pages
     * for data of what's currently sellable under the current screen context.
     *
     * <p>Targets</p>
     * <ul>
     *  <li>{@code MAIN_PAGE | SEARCH_PAGE}: {@code BazaarSlots.OVERVIEW_PAGE.SELL_SACKS},</li>
     *  <li>{@code PRODUCTS_CATALOG_PAGE}: {@code BazaarSlots.PRODUCTS_CATALOG_PAGE.SELL_SACKS}</li>
     * </ul>
     */
    public static SellableItemList parseSackOrders(ItemStack sellSacksStack) {
        var parsed = SellableItemList.parse(sellSacksStack, "parseSackOrders");

        PlayerActionUtil.notifyAll(
                "SellSacks parsed: %d known items | %d folded to \"Other Items\""
                        .formatted(parsed.items().size(), parsed.otherItems().map(SellableItemList.OtherItems::volume).orElse(0)),
                NotificationType.GUI);

        return parsed;
    }
}
