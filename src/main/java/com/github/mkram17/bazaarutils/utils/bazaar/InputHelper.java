package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import lombok.Getter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public abstract class InputHelper<T> implements BUToggleableFeature, ItemButton {
    @Getter
    protected String name;

    protected abstract Item getButtonItem();

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
        if (!isEnabled() || !inCorrectScreen()) {
            resetState();

            return;
        }

        state = makeState(event);
    }

    public void onReplaceItem(ReplaceItemEvent event) {
        if (!(isEnabled()
                && inCorrectScreen()
                && state.isPresent()
                && shouldReplaceItem(event))) {
            return;
        }

        ItemStack stack = getReplacementItem();

        stack.set(CustomDataComponents.CUSTOM_SIZE, String.valueOf(getButtonItemStackSize(state.get())));
        stack.set(DataComponentTypes.CUSTOM_NAME, getButtonItemText(state.get()));

        event.setReplacement(stack);
    }

    public void onSlotClicked(SlotClickEvent event) {
        if (!(isEnabled()
                && inCorrectScreen()
                && wasButtonClicked(event))) {
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

    protected abstract Text getButtonItemText(T state);

    protected abstract String getButtonItemStackSize(T state);

    //    Action stuff

    protected abstract void handleAction(T state);
}