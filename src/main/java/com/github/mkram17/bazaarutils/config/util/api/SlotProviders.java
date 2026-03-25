package com.github.mkram17.bazaarutils.config.util.api;

import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemBuilder;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

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

    public static ItemBuilder stack(Item item) {
        return ItemBuilder.of(item);
    }

    public static ItemBuilder stack(Item item, int count) {
        return ItemBuilder.of(item, count);
    }
}