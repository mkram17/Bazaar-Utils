package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.KeybindConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ModifyIndicator;
import com.github.mkram17.bazaarutils.utils.resources.BazaarConversions;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ItemModifier
public class DimNonBazaarItems implements SlotHighlight, LoreModifier {

    private static final int DIM_TINT = 0xAA000000;

    private static final Map<String, Boolean> NOT_SELLABLE_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled() {
        return InventoryConfig.DIM_NON_BAZAAR_ITEMS_TOGGLE;
    }

    @Override
    public ModifyIndicator.IndicatorPlacement indicatorPlacement() {
        return ModifyIndicator.IndicatorPlacement.AT_MODIFICATION;
    }

    @Override
    public HighlightStyle getHighlightStyle() {
        return HighlightStyle.FOREGROUND;
    }

    public KeyMapping getKeybind() {
        return KeybindConfig.DIMMED_EXPAND;
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.any();

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public final EnumSet<ModifierSource> MODIFIER_SOURCES = EnumSet.of(ModifierSource.PLAYER_INVENTORY);

    @Override
    public EnumSet<ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES;
    }

    public DimNonBazaarItems() {}

    @Override
    public boolean appliesTo(ItemStack stack, @Nullable Slot slot, @Nullable ScreenContext context) {
        return appliesTo(stack);
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Optional<ProductInfo> product = ProductInfo.fromItemStack(stack);

        return product.map(it -> NOT_SELLABLE_CACHE.computeIfAbsent(it.getProductId(), k -> false)).orElse(true);
}

    @Override
    public Optional<Integer> highlightColor(ItemStack stack, @Nullable Slot slot) {
        return Optional.of(DIM_TINT);
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
        if (lore.isEmpty()) return Result.UNMODIFIED;
        if (isExpandKeyHeld()) return Result.UNMODIFIED;

        return withMerger(lore, merger -> {
            // Preserve the coloured item name on the first line.
            if (merger.canRead()) merger.copy();

            // Drain every remaining source line without writing to destination,
            // so addRemaining() finds nothing left and adds nothing.
            while (merger.canRead()) merger.read();

            merger.add(Component.empty());
            merger.add(withAtModificationIndicator(
                    Component.literal("Hold ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
                            .append(getKeybind().getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("]").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(" to view full lore").withStyle(ChatFormatting.DARK_GRAY))));

            return Result.HANDLED;
        });
    }

    @Override
    public Result onClick(ItemStack stack, int button, @Nullable Slot slot, @Nullable ScreenContext context) {
        if (isExpandKeyHeld()) return Result.UNMODIFIED;

        return Result.CONSUMED;
    }

    private boolean isExpandKeyHeld() {
        Window window = Minecraft.getInstance().getWindow();
        InputConstants.Key key = InputConstants.getKey(getKeybind().saveString());

        return InputConstants.isKeyDown(window, key.getValue());
    }
}