package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.screen.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;

import java.util.Optional;

public abstract class InputHelper<T> implements ItemButton {
    @Getter
    protected String name;

    /**
     * The market action which this helper is operating on (to buy, to sell; instant, order).
     */
    protected abstract TransactionType getTransactionType();

//    Event cycle routines stuff

    @Getter
    @NotNull
    private transient Optional<T> state = Optional.empty();

    protected abstract Optional<T> makeState(ChestLoadedEvent event);

    protected void resetState() {
        state = Optional.empty();
    }

    public InputHelper(@NotNull String name) {
        this.name = name;
    }

    public void onChestLoaded(ChestLoadedEvent event) {
        if (!inCorrectScreen()) {
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

    //    Screen/menu/inventory stuff

    protected abstract boolean inCorrectScreen();

    //    Button stuff

    protected abstract Component getButtonItemText(T state);

    protected abstract String getButtonItemStackSize(T state);

    //    Action stuff

    protected abstract void handleAction(T state);
}