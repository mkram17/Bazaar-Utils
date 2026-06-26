package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CustomItemButton {
    //TODO make flip helper and custom order use this instead of their own settings variables when possible
    protected int slotNumber;
    @Getter @Setter
    protected transient ItemStack replacementItem;
    protected static final Holder<SoundEvent> BUTTON_SOUND = SoundEvents.UI_BUTTON_CLICK;
    protected static final float BUTTON_VOLUME = .2f;

    protected void onGuiLoad(ChestLoadedEvent event) {

    }

    protected boolean shouldReplaceItem(ReplaceItemEvent event) {
        return event.getSlotId() == slotNumber;
    }

    protected boolean wasButtonSlotClicked(SlotClickEvent event) {
        return (event.slotId == slotNumber);
    }

    public Option<Boolean> createBooleanOption(String name, String description, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .binding(true,
                        getter,
                        setter)
                .description(OptionDescription.of(Component.literal(description)))
                .controller(BUConfigGui::createBooleanController)
                .build();
    }

    public <T extends Enum<T>> Option<T> createEnumOption(String name, String description, Class<T> enumClass, T def, Supplier<T> getter, Consumer<T> setter) {
        return Option.<T>createBuilder()
                .name(Component.literal(name))
                .binding(def,
                        getter,
                        setter)
                .description(OptionDescription.of(Component.literal(description)))
                .controller(opt -> BUConfigGui.createEnumController(opt, enumClass))
                .build();
    }
}
