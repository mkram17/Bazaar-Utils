// Adapted from https://github.com/meowdding/SkyOcean/blob/main/src/main/kotlin/me/owdding/skyocean/features/item/modifier/ItemModifier.kt
package com.github.mkram17.bazaarutils.utils.minecraft.item.modifier;

import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public interface AbstractItemModifier extends ToggleableFeature {
    int LOWEST = 1_000_000;
    int LOW = 100_000;
    int NORMAL = 0;
    int HIGH = -100_000;
    int HIGHEST = -1_000_000;

    Component INDICATOR = Component.literal("₿").withStyle(style -> style.withColor(ChatFormatting.GOLD).withItalic(false));
    Component INDICATOR_WITH_SPACE = Component.empty().append(INDICATOR).append(" ");
    Component SPACE_WITH_INDICATOR = Component.empty().append(" ").append(INDICATOR);
    Component INDICATOR_LABEL = Component.literal("Modified by BazaarUtils").withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false));
    Component INDICATOR_LABEL_LINE = Component.empty().append(INDICATOR_WITH_SPACE).append(INDICATOR_LABEL);

    enum ModifierSource {
        INVENTORY,
        PLAYER_INVENTORY,
        HOTBAR,
        EQUIPMENT;

        public static final List<ModifierSource> ALL = List.of(values());
    }

    enum BazaarUtilsModifyIndicator implements Translatable {
        PREFIX, SUFFIX, LORE, DISABLED;

        @Override
        public String getTranslationKey() {
            return "bazaarutils.config.modify_indicator." + name().toLowerCase() + ".label";
        }
    }

    record Result(boolean modified, boolean propagateFurther) {
        public static final Result CONSUME = new Result(true, false);
        public static final Result MODIFIED = new Result(true, true);
        public static final Result CANCELLED = new Result(false, false);
        public static final Result UNMODIFIED = new Result(false, true);
    }

    default int getPriority() {
        return NORMAL;
    }

    default List<ModifierSource> getModifierSources() {
        return ModifierSource.ALL;
    }

    default boolean appliesToScreen(ScreenContext context) {
        return true;
    }

    default boolean appliesTo(ItemStack stack, @Nullable Slot slot) {
        return appliesTo(stack);
    }

    boolean appliesTo(ItemStack stack);

    default Optional<Item> itemOverride(ItemStack stack) {
        return Optional.empty();
    }

    default Optional<Component> nameOverride(ItemStack stack) {
        return Optional.empty();
    }

    default Optional<ItemStack> backgroundItem(ItemStack stack) {
        return Optional.empty();
    }

    default Optional<Component> itemCountOverride(ItemStack stack) {
        return Optional.empty();
    }

    default Optional<Integer> highlightColor(ItemStack stack) {
        return Optional.empty();
    }

    default Optional<DataComponentPatch> patchComponents(ItemStack stack) {
        return Optional.empty();
    }

    default Result modifyStack(ItemStack stack) {
        return Result.UNMODIFIED;
    }

    default Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous) {
        return Result.UNMODIFIED;
    }
}