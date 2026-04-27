package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.events.minecraft.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.utils.minecraft.item.groups.StateItemGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;

public interface ItemButton {
    Item DEFAULT_ITEM = Items.BARRIER;

    float BUTTON_VOLUME = 0.2f;
    Holder<SoundEvent> BUTTON_SOUND = SoundEvents.UI_BUTTON_CLICK;

    int getSlotIndex();
    ItemRef getItemRef();

    default ItemStack resolveStack() {
        return switch (getItemRef()) {
            case ItemRef.Direct(Item item) -> new ItemStack(item);
            case ItemRef.ById(var id) -> resolveId(id.get());
            case ItemRef.Stateful<?> stateful -> resolveStatefulStack(stateful);
        };
    }

    default ItemStack getReplacementItem(int size) {
        var stack = resolveStack();

        stack.setCount(size);

        return stack;
    }

    default ItemStack getReplacementItem() {
        return getReplacementItem(1);
    }

    default boolean shouldReplaceItem(ReplaceItemEvent event) {
        return event.getSlotId() == getSlotIndex();
    }

    default boolean wasButtonClicked(SlotClickEvent event) {
        return event.getSlot().getContainerSlot() == getSlotIndex();
    }

    private static ItemStack resolveId(String rawId) {
        ItemStack resolved = ItemsRepo.resolve(rawId);

        return resolved != null ? resolved : DEFAULT_ITEM.getDefaultInstance();
    }

    private static <S> ItemStack resolveStatefulStack(ItemRef.Stateful<S> stateful) {
        ItemStack resolved = stateful.source()
                .map(source -> switch (source) {
                    case ItemRef.Direct(Item item) -> new ItemStack(item);
                    case ItemRef.ById(var id) -> resolveId(id.get());
                    case ItemRef.Stateful<?> ignored -> throw new IllegalStateException("Stateful ItemRef cannot nest another Stateful as its source");
                })
                .orElse(new ItemStack(DEFAULT_ITEM));

        S state = stateful.state().get();

        for (StateItemGroup<S> group : stateful.groups()) {
            if (group.contains(resolved.getItem())) return new ItemStack(group.forState(state, resolved.getItem()));
        }

        return resolved;
    }
}