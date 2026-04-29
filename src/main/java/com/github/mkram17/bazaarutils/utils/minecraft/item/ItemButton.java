package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.features.ItemModifiers;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.groups.StateItemGroup;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ModifyIndicator;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

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
    default ModifyIndicator.IndicatorPlacement indicatorPlacement() {
        return ModifyIndicator.IndicatorPlacement.NAME_PREFIX;
    }

    @Override
    default boolean appliesTo(ItemStack stack, @Nullable Slot slot, @Nullable ScreenContext context) {
        return slot != null && slot.getContainerSlot() == getSlotIndex();
    }

    @Override
    default boolean appliesTo(ItemStack stack) {
        return false;
    }

    @Override
    default Optional<ItemStack> stackOverride(ItemStack stack, @Nullable Slot slot) {
        return Optional.of(resolveStack());
    }

    List<ModifierSource> MODIFIER_SOURCES = List.of(ModifierSource.CONTAINER);

    @Override
    default List<ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES;
    }

    @Override
    default Result onClick(ItemStack stack, int button, @Nullable Slot slot, @Nullable ScreenContext context) {
        return onButtonClicked(button);
    }

    default void retriggerModifier() {
        var context = ScreenManager.getInstance().currentOrNull();
        if (context == null) return;

        var screen = context.as(AbstractContainerScreen.class);
        if (screen.isEmpty()) return;

        screen.get().getMenu().slots.stream()
                .filter(slot -> slot.getContainerSlot() == getSlotIndex())
                .findFirst()
                .ifPresent(slot -> {
                    ItemModifiers.clear(slot.getItem());
                    ItemModifiers.tryModify(slot.getItem(), ModifierSource.CONTAINER, context, slot);
                });
    }

    default ItemStack resolveStack() {
        return switch (getItemRef()) {
            case ItemRef.Direct(Item item) -> new ItemStack(item);
            case ItemRef.ById(var id) -> resolveId(id.get());
            case ItemRef.Stateful<?> stateful -> resolveStatefulStack(stateful);
        };
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