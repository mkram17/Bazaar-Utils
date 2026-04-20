package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import lombok.Getter;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

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
        retriggerModifier();
    }

    public InputHelper(@NotNull String name) {
        this.name = name;
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        return state.isPresent() && ItemButton.super.appliesTo(stack);
    }

    @Override
    public Optional<Component> nameOverride(ItemStack stack) {
        return state.map(this::getButtonItemText);
    }

    @Override
    public Optional<DataComponentPatch> patchComponents(ItemStack stack) {
        return state.map(state -> DataComponentPatch.builder()
                .set(CustomDataComponents.CUSTOM_SIZE, getButtonItemStackSize(state))
                .build());
    }

    @Override
    public Result onButtonClicked(int button) {
        if (state.isEmpty()) {
            Util.logMessage("Cannot handle action for " + name + ", state is empty.");

            return Result.CANCELLED;
        }

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        handleAction(state.get());
        resetState();

        return Result.CONSUME;
    }

    public void onChestLoaded(ChestLoadedEvent event) {
        if (!appliesToScreen(ScreenManager.getInstance().current())) {
            resetState();
            return;
        }

        state = makeState(event);
    }

    //    Button stuff

    protected abstract Component getButtonItemText(T state);

    protected abstract String getButtonItemStackSize(T state);

    //    Action stuff

    protected abstract void handleAction(T state);
}