package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SellParser;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.SellTarget;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import net.minecraft.world.item.ItemStack;

import java.util.*;

@ItemModifier
public class InstantSellHighlight implements AbstractItemModifier {
    @Override
    public boolean isEnabled() {
        return InventoryConfig.INSTANT_SELL_HIGHLIGHT_TOGGLE;
    }

    @Override
    public boolean appliesToScreen(Optional<ScreenContext> context) {
        return context.map(it -> it.isAnyOf(BazaarScreens.MAIN_PAGE, BazaarScreens.ITEMS_GROUP_PAGE, BazaarScreens.ITEM_PAGE)).orElse(false);
    }

    public InstantSellHighlight() {
        super();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        return SellParser.Targets.get(stack)
                .map(target -> target == SellTarget.INSTANT_SELL)
                .orElse(false);
    }

    @Override
    public List<ModifierSource> getModifierSources() {
        return List.of(ModifierSource.PLAYER_INVENTORY, ModifierSource.HOTBAR);
    }

    @Override
    public Optional<Integer> highlightColor(ItemStack stack) {
        return Optional.of(InventoryConfig.INSTANT_SELL_HIGHLIGHT_COLOR);
    }
}