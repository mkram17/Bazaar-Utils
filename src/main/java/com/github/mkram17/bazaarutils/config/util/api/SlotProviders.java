package com.github.mkram17.bazaarutils.config.util.api;

import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
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

    public static ItemStack named(Item item, Text name) {
        return named(item, 1, name);
    }

    public static ItemStack named(Item item, int count, Text name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponentTypes.CUSTOM_NAME, name);

        return stack;
    }

    public static ItemStack hiddenTooltip(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);

        stack.set(DataComponentTypes.TOOLTIP_DISPLAY, new TooltipDisplayComponent(true, new ReferenceLinkedOpenHashSet<>()));

        return stack;
    }
}