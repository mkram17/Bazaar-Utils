package com.github.mkram17.bazaarutils.config.util.api;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public final class SlotProviders {
    private static final Map<String, SlotProvider> REGISTRY = new HashMap<>();

    private SlotProviders() {}

    public static void register(String key, SlotProvider provider) {
        REGISTRY.put(key, provider);
    }

    public static SlotProvider get(String key) {
        if (key == null || key.isEmpty()) return slot -> ItemStack.EMPTY;

        return REGISTRY.getOrDefault(key, slot -> ItemStack.EMPTY);
    }

    public static SlotStack stack(Item item) {
        return new SlotStack(item, 1);
    }

    public static SlotStack stack(Item item, int count) {
        return new SlotStack(item, count);
    }

    public static final class SlotStack {

        private final ItemStack stack;

        private SlotStack(Item item, int count) {
            this.stack = new ItemStack(item, count);
        }

        public SlotStack named(Text name) {
            stack.set(DataComponentTypes.CUSTOM_NAME, name);
            return this;
        }

        public SlotStack named(String name) {
            return named(Text.literal(name));
        }

        public SlotStack locked() {
            stack.set(CustomDataComponents.SLOT_SELECTOR_LOCKED, true);
            return this;
        }

        public SlotStack hideTooltip() {
            stack.set(DataComponentTypes.TOOLTIP_DISPLAY, new TooltipDisplayComponent(true, ReferenceSortedSets.emptySet()));
            return this;
        }

//      we hide attributes by default
//        public SlotStack hideAttributes() {
//            TooltipDisplayComponent current = stack.getOrDefault(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT);
//            stack.set(DataComponentTypes.TOOLTIP_DISPLAY, current.with(DataComponentTypes.ATTRIBUTE_MODIFIERS, true));
//            return this;
//        }

        public ItemStack build() {
            TooltipDisplayComponent current = stack.getOrDefault(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT);
            stack.set(DataComponentTypes.TOOLTIP_DISPLAY, current.with(DataComponentTypes.ATTRIBUTE_MODIFIERS, true));

            return stack;
        }
    }
}