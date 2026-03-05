package com.github.mkram17.bazaarutils.utils.minecraft;

import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public interface ItemButton {
    RegistryEntry<SoundEvent> BUTTON_SOUND = SoundEvents.UI_BUTTON_CLICK;
    float BUTTON_VOLUME = 0.2f;
    int getSlotIndex();


    default ItemStack getReplacementItem(int size) {
        return new ItemStack(resolveItem(), size);
    }

    default ItemStack getReplacementItem() {
        return getReplacementItem(1);
    }

    default boolean shouldReplaceItem(ReplaceItemEvent event) {
        return event.getSlotId() == getSlotIndex();
    }

    default boolean wasButtonClicked(SlotClickEvent event) {
        return event.getSlotId() == getSlotIndex();
    }
}
