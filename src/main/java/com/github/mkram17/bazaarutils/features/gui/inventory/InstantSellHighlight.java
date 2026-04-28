package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.data.SellableAPI;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.SlotHighlight;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ItemModifier
public class InstantSellHighlight implements SlotHighlight {
    @Override
    public boolean isEnabled() {
        return InventoryConfig.INSTANT_SELL_HIGHLIGHT_TOGGLE;
    }

    @Override
    public HighlightStyle getHighlightStyle() {
        return InventoryConfig.INSTANT_SELL_HIGHLIGHT_STYLE;
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.MAIN_PAGE, BazaarScreenType.SEARCH_PAGE, BazaarScreenType.PRODUCTS_CATALOG_PAGE, BazaarScreenType.PRODUCT_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public final EnumSet<ModifierSource> MODIFIER_SOURCES = EnumSet.of(ModifierSource.PLAYER_INVENTORY, ModifierSource.HOTBAR);

    @Override
    public EnumSet<ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES; // to prevent instantiating the LIST every single iteration
    }

    public InstantSellHighlight() {
        super();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        return SellableAPI.Targets.get(stack)
                .map(target -> target.isInstant() && target.isSell())
                .orElse(false);
    }

    @Override
    public Optional<Integer> highlightColor(ItemStack stack, @Nullable Slot slot) {
        return Optional.of(InventoryConfig.INSTANT_SELL_HIGHLIGHT_COLOR);
    }
}