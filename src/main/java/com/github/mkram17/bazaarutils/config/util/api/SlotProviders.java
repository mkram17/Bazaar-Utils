package com.github.mkram17.bazaarutils.config.util.api;

import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import tech.thatgravyboat.skyblockapi.utils.builders.ItemBuilder;

import java.util.*;
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

                return stack(occupant.get().resolveStack())
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


    public static StackBuilder stack(Item item) {
        return new StackBuilder(item, 1, null);
    }

    public static StackBuilder stack(Item item, int count) {
        return new StackBuilder(item, count, null);
    }

    public static StackBuilder stack(ItemStack source) {
        return new StackBuilder(null, 1, source);
    }

    public static final class StackBuilder {
        private final Item item;
        private final int count;
        private final ItemStack source;
        private String name = null;
        private boolean locked = false;
        private boolean hideTooltip = false;

        private StackBuilder(Item item, int count, ItemStack source) {
            this.item = item;
            this.count = count;
            this.source = source;
        }

        public StackBuilder named(String name) {
            this.name = name;
            return this;
        }

        public StackBuilder locked() {
            this.locked = true;
            return this;
        }

        public StackBuilder hideTooltip() {
            this.hideTooltip = true;
            return this;
        }

        public ItemStack build() {
            Item resolvedItem = source != null ? source.getItem() : item;

            return ItemBuilder.Companion.invoke(resolvedItem, builder -> {
                if (source != null) builder.applyFrom(source);

                builder.setCount(count);

                if (name != null) builder.name(name);
                if (locked) builder.set(CustomDataComponents.SLOT_SELECTOR_LOCKED, true);
                if (hideTooltip) {
                    TooltipDisplay existing = source != null ? source.get(DataComponents.TOOLTIP_DISPLAY) : null;
                    builder.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, existing != null ? existing.hiddenComponents() : new LinkedHashSet<>()));
                }

                return kotlin.Unit.INSTANCE;
            });
        }
    }
}