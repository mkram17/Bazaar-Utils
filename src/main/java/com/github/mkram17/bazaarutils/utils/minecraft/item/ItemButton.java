package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.config.util.api.ResourcefulConfigItems;
import com.github.mkram17.bazaarutils.events.screen.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.groups.StateItemGroup;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ItemModifiers;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;

import java.util.List;
import java.util.Optional;

public interface ItemButton extends AbstractItemModifier {
    Item DEFAULT_ITEM = Items.BARRIER;

    float BUTTON_VOLUME = 0.2f;
    Holder<SoundEvent> BUTTON_SOUND = SoundEvents.UI_BUTTON_CLICK;

    int getSlotIndex();
    ItemRef getItemRef();
    Result onButtonClicked(int button);

    @Override
    default boolean isEnabled() {
        return true;
    }

    @Override
    default boolean appliesTo(ItemStack stack, @Nullable Slot slot) {
        return slot != null && slot.getContainerSlot() == getSlotIndex();
    }

    @Override
    default boolean appliesTo(ItemStack stack) {
        return false;
    }

    @Override
    default Optional<Item> itemOverride(ItemStack stack) {
        return Optional.of(resolveItem());
    }

    @Override
    default List<ModifierSource> getModifierSources() {
        return List.of(ModifierSource.INVENTORY);
    }

    // Simple helper such that state changes on consumers may recompute the modifier
    default void retriggerModifier() {
        AbstractContainerScreen<?> screen = ScreenManager.getCurrentlyHandledScreen(AbstractContainerScreen.class).orElse(null);
        if (screen == null) return;

        screen.getMenu().slots.stream()
                .filter(slot -> slot.getContainerSlot() == getSlotIndex())
                .findFirst()
                .ifPresent(slot -> {
                    ItemModifiers.clear(slot.getItem());
                    ItemModifiers.tryModify(slot.getItem(), AbstractItemModifier.ModifierSource.INVENTORY, slot);
                });
    }

    default Item resolveItem() {
        return switch (getItemRef()) {
            case ItemRef.Direct(Item item) -> item;
            case ItemRef.ById(var id) -> resolveId(id.get());
            case ItemRef.Stateful<?> stateful -> resolveStateful(stateful);
        };
    }

    private static Item resolveId(String rawId) {
        Item resolved = ResourcefulConfigItems.resolve(rawId);

        return resolved != null ? resolved : DEFAULT_ITEM;
    }

    private static <S> Item resolveStateful(ItemRef.Stateful<S> stateful) {
        Item resolved = stateful.source()
                .map(source -> switch (source) {
                    case ItemRef.Direct(Item item) -> item;
                    case ItemRef.ById(var id) -> resolveId(id.get());
                    case ItemRef.Stateful<?> ignored -> throw new IllegalStateException("Stateful ItemRef cannot nest another Stateful as its source");
                })
                .orElse(DEFAULT_ITEM);

        S state = stateful.state().get();

        for (StateItemGroup<S> group : stateful.groups()) {
            if (group.contains(resolved)) return group.forState(state, resolved);
        }

        return resolved;
    }
}