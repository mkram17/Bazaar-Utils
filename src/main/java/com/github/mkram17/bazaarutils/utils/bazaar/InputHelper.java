package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import lombok.Getter;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.Callable;

public abstract class InputHelper<T> implements ItemButton {
    protected static final BazaarLogger LOG = BazaarLogger.of(InputHelper.class);

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

    protected abstract Optional<T> makeState(ContainerLoadedEvent event);

    protected void resetState() {
        state = Optional.empty();
        retriggerModifier();
    }

    public InputHelper(@NotNull String name) {
        this.name = name;
    }

    @Override
    public boolean appliesTo(ItemStack stack, @Nullable Slot slot, @Nullable ScreenContext context) {
        return state.isPresent() && ItemButton.super.appliesTo(stack, slot, context);
    }

    @Override
    public Optional<Component> nameOverride(ItemStack stack, @Nullable Slot slot) {
        return state.map(this::getButtonItemText);
    }

    @Override
    public Optional<DataComponentPatch> patchComponents(ItemStack stack, @Nullable Slot slot) {
        return state.map(state -> DataComponentPatch.builder()
                .set(CustomDataComponents.CUSTOM_SIZE, getButtonItemStackSize(state))
                .build());
    }

    public void onContainerLoaded(ContainerLoadedEvent event) {
        if (!inCorrectScreen(event)) {
            resetState();

            return;
        }

        state = makeState(event);

        if (state.isEmpty()) LOG.info("{}: makeState returned empty", name);
    }

    public Result onButtonClicked(int button) {
        if (state.isEmpty()) {
            LOG.warn("Button clicked with no state — action dropped for '{}'", name);

            return Result.CANCELLED;
        }

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        handleAction(state.get(), this::resetState);

        return Result.CONSUMED;
    }

    //    Button stuff

    protected abstract Component getButtonItemText(T state);

    protected abstract String getButtonItemStackSize(T state);

    //    Action stuff

    protected abstract void handleAction(T state, Runnable callback);
}