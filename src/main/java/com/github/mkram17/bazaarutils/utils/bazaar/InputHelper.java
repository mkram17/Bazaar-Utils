package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.teamresourceful.resourcefulconfig.api.types.info.ListEntryInfoProvider;
import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;

import java.util.Optional;

public abstract class InputHelper<T> implements ItemButton, ScreenConstrained, ListEntryInfoProvider {
    @Getter
    protected String name;

    /**
     * The market action which this helper is operating on (to buy, to sell; instant, order).
     */
    protected abstract TransactionType getTransactionType();

    /**
     * The item shown as this button. Declared here rather than resolved here because each helper
     * exposes it as its own {@code @ConfigEntry} — resourcefulconfig only reads fields declared on
     * the concrete class, so the field cannot be hoisted even though its use can.
     */
    public abstract String getItemId();

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(this::getItemId);
    }

    /**
     * The subtitle of this helper's row in the config list. Every helper says the same thing here;
     * what distinguishes them is {@code getTitle}, which each one supplies.
     */
    @Override
    public Component getDescription(int index) {
        return Component.literal("Slot " + getSlotIndex() + " · " + resolveItem().getName().getString());
    }

//    Event cycle routines stuff

    @Getter
    @NotNull
    private transient Optional<T> state = Optional.empty();

    protected abstract Optional<T> makeState(ContainerLoadedEvent event);

    protected void resetState() {
        state = Optional.empty();
    }

    public InputHelper(@NotNull String name) {
        this.name = name;
    }

    public void onContainerLoaded(ContainerLoadedEvent event) {
        if (!inCorrectScreen(event)) {
            resetState();

            return;
        }

        state = makeState(event);
    }

    public void onReplaceItem(ReplaceItemEvent event) {
        if (!(inCorrectScreen()
                && state.isPresent()
                && shouldReplaceItem(event))) {
            return;
        }

        ItemStack stack = getReplacementItem();

        stack.set(CustomDataComponents.CUSTOM_SIZE, String.valueOf(getButtonItemStackSize(state.get())));
        stack.set(DataComponents.CUSTOM_NAME, getButtonItemText(state.get()));

        event.setReplacement(stack);
    }

    public void onSlotClicked(SlotClickEvent event) {
        if (!(inCorrectScreen() && wasButtonClicked(event))) {
            return;
        }

        if (state.isEmpty()) {
            Util.logMessage("Cannot handle action for " + name + ", state is empty.");

            return;
        }

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        handleAction(state.get());
        resetState();
    }

    //    Button stuff

    protected abstract Component getButtonItemText(T state);

    protected abstract String getButtonItemStackSize(T state);

    //    Action stuff

    protected abstract void handleAction(T state);
}