package com.github.mkram17.bazaarutils.utils.minecraft.item.modifier;

import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

// Adapted from https://github.com/meowdding/SkyOcean/blob/main/src/main/kotlin/me/owdding/skyocean/features/item/modifier/ItemModifier.kt

/**
 * @see Result
 */
public interface AbstractItemModifier extends ToggleableFeature, ScreenConstrained {
    enum ModifierSource {
        CONTAINER,
        PLAYER_INVENTORY,
        EQUIPMENT,
        HOTBAR;

        public static final EnumSet<ModifierSource> ALL = EnumSet.allOf(ModifierSource.class);
    }

    /**
     * @see Priority
     */
    default int getPriority() {
        return Priority.NORMAL;
    }

    default Collection<ModifierSource> getModifierSources() {
        return ModifierSource.ALL;
    }

    boolean appliesTo(ItemStack stack);

    default boolean appliesTo(ItemStack stack, @Nullable Slot slot, @Nullable ScreenContext context) {
        return appliesTo(stack);
    }

    default Optional<Item> itemOverride(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Optional<ItemStack> stackOverride(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Optional<ItemStack> backgroundItem(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Optional<Component> nameOverride(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Optional<Component> itemCountOverride(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Optional<Integer> borderColor(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Optional<Integer> backgroundColor(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Optional<Integer> foregroundColor(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Optional<DataComponentPatch> patchComponents(ItemStack stack, @Nullable Slot slot) {
        return Optional.empty();
    }

    default Result appendComponents(ItemStack stack, List<ClientTooltipComponent> components, @Nullable ScreenContext context) {
        return Result.UNMODIFIED;
    }

    default Result onClick(ItemStack stack, int button, @Nullable Slot slot, @Nullable ScreenContext context) {
        return Result.UNMODIFIED;
    }

    default Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
        return Result.UNMODIFIED;
    }

    /**
     * Declares where this modifier prefers the indicator to be stamped when the
     * global config is {@link ModifyIndicator#AT_MODIFICATION}.
     */
    default ModifyIndicator.IndicatorPlacement indicatorPlacement() {
        return ModifyIndicator.IndicatorPlacement.LORE_LINE;
    }
}