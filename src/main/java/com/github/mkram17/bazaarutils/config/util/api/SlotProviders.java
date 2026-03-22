package com.github.mkram17.bazaarutils.config.util.api;

import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class SlotProviders {
    private static final Map<String, SlotProvider> REGISTRY = new HashMap<>();

    private SlotProviders() {}

    public static void register(String key, SlotProvider provider) {
        REGISTRY.put(key, provider);
    }

    public static void registerDynamic(String key, Supplier<List<? extends ItemButton>> entries, SlotProvider provider) {
        register(key, (slotIndex, selectedSlotIndex) -> {
            Optional<? extends ItemButton> occupant = entries.get().stream()
                    .filter(entry -> entry.getSlotIndex() == slotIndex)
                    .findFirst();

            if (occupant.isPresent()) {
                int occupantIndex = entries.get().indexOf(occupant.get()) + 1;

                boolean isSelf = occupant.get().getSlotIndex() == selectedSlotIndex;

                return stack(occupant.get().resolveItem())
                        .named(isSelf
                                ? "Currently selected slot"
                                : "Slot taken by Button #" + occupantIndex
                        )
                        .locked()
                        .build();
            }

            return provider.getStack(slotIndex, selectedSlotIndex);
        });
    }

    public static SlotProvider get(String key) {
        if (key == null || key.isEmpty()) return (slotIndex, selectedSlotIndex) -> ItemStack.EMPTY;

        return REGISTRY.getOrDefault(key, (slotIndex, selectedSlotIndex) -> ItemStack.EMPTY);
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