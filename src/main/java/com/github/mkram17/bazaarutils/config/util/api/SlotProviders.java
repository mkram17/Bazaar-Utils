package com.github.mkram17.bazaarutils.config.util.api;

import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemBuilder;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public final class SlotProviders {
    /** Last addressable index of the 4x9 grid every container-button layout is drawn on. */
    public static final int MAX_SLOT_INDEX = 35;

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

    /**
     * The first slot of {@code key}'s layout that is not already spoken for — where a newly added
     * button goes by default. Falls back to the last slot when every one of them is taken.
     */
    public static int firstUnlockedSlot(String key) {
        SlotProvider provider = get(key);

        return IntStream.rangeClosed(0, MAX_SLOT_INDEX)
                .filter(slotIndex -> !provider.getStack(slotIndex).getOrDefault(CustomDataComponents.SLOT_SELECTOR_LOCKED, false))
                .findFirst()
                .orElse(MAX_SLOT_INDEX);
    }

    /**
     * Wraps a slot-to-stack lookup with the two things every bazaar layout wants: out-of-grid
     * indices resolve to nothing, and a slot the lookup has no entry for is drawn as background
     * filler rather than left blank.
     */
    public static SlotProvider layout(IntFunction<ItemStack> slots) {
        return (slotIndex, selectedSlotIndex) -> {
            if (slotIndex < 0 || slotIndex > MAX_SLOT_INDEX) return ItemStack.EMPTY;

            ItemStack stack = slots.apply(slotIndex);

            return stack.isEmpty() ? filler() : stack;
        };
    }

    /** The pane a layout shows for a slot Hypixel leaves empty. */
    public static ItemStack filler() {
        return stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
    }

    /** A named, non-selectable stack — how a layout marks a slot Hypixel already uses. */
    public static ItemStack locked(Item item, String name) {
        return locked(item, 1, name);
    }

    /** As {@link #locked(Item, String)}, for the entries whose stack size carries meaning. */
    public static ItemStack locked(Item item, int count, String name) {
        return stack(item, count).named(name).locked().build();
    }

    public static ItemBuilder stack(Item item) {
        return ItemBuilder.of(item);
    }

    public static ItemBuilder stack(Item item, int count) {
        return ItemBuilder.of(item, count);
    }
}