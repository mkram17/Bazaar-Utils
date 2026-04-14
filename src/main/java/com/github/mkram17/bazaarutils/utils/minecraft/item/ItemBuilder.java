package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

public final class ItemBuilder {
    private final Item item;
    private int count = 1;
    private Component name = null;
    private boolean locked = false;
    private boolean hideTooltip = false;

    private ItemBuilder(Item item) {
        this.item = item;
    }

    public static ItemBuilder of(Item item) {
        return new ItemBuilder(item);
    }

    public static ItemBuilder of(Item item, int count) {
        return new ItemBuilder(item).count(count);
    }

    public ItemBuilder count(int count) {
        this.count = count;
        return this;
    }

    public ItemBuilder named(String name) {
        return named(Component.literal(name));
    }

    public ItemBuilder named(Component name) {
        this.name = name;
        return this;
    }

    public ItemBuilder locked() {
        this.locked = true;
        return this;
    }

    public ItemBuilder hideTooltip() {
        this.hideTooltip = true;
        return this;
    }

    public ItemStack build() {
        var stack = new ItemStack(item, count);

        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, name);
        }
        if (locked) {
            stack.set(CustomDataComponents.SLOT_SELECTOR_LOCKED, true);
        }

        TooltipDisplay tooltip = hideTooltip
                ? new TooltipDisplay(true, ReferenceSortedSets.emptySet())
                : stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        stack.set(DataComponents.TOOLTIP_DISPLAY, tooltip.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));

        return stack;
    }
}