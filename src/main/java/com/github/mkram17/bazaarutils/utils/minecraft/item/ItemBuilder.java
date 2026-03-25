package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class ItemBuilder {
    private final Item item;
    private int count = 1;
    private Text name = null;
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
        return named(Text.literal(name));
    }

    public ItemBuilder named(Text name) {
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
            stack.set(DataComponentTypes.CUSTOM_NAME, name);
        }
        if (locked) {
            stack.set(CustomDataComponents.SLOT_SELECTOR_LOCKED, true);
        }

        TooltipDisplayComponent tooltip = hideTooltip
                ? new TooltipDisplayComponent(true, ReferenceSortedSets.emptySet())
                : stack.getOrDefault(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT);

        stack.set(DataComponentTypes.TOOLTIP_DISPLAY, tooltip.with(DataComponentTypes.ATTRIBUTE_MODIFIERS, true));

        return stack;
    }
}