package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public abstract class RestrictionHelper<T extends RestrictionHelper.RestrictionState> extends BUListener implements BUToggleableFeature {
    public interface RestrictionState {
        @NotNull
        ItemInfo targetItem();
        @NotNull
        List<RestrictionControl<?>> triggeredRestrictors();
    }

    @Getter
    protected String name;

    protected abstract int getClicksOverride();
    protected abstract String getMessagePrefix();
    protected abstract List<RestrictionControl<?>> getRestrictors();

    @Getter
    private transient int clicks = 0;
    private transient boolean isRestricted = true;
    private transient Optional<T> state = Optional.empty();

    protected abstract Optional<T> makeState(ChestLoadedEvent event);

    protected void resetState() {
        state = Optional.empty();
    }

    public RestrictionHelper(String name) {
        super();
        this.name = name;
    }

    @Override
    protected void registerFabricEvents() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            clicks = 0;
            isRestricted = true;
            state = Optional.empty();
        });
    }

    @EventHandler
    public void onChestLoaded(ChestLoadedEvent event) {
        if (!(isEnabled() && inCorrectScreen())) {
            resetState();
            return;
        }

        state = makeState(event);
        isRestricted = state.map(state -> !state.triggeredRestrictors().isEmpty()).orElse(true);
        clicks = 0;
    }

    @EventHandler
    public void onSlotClicked(SlotClickEvent event) {
        if (!(isEnabled() && inCorrectScreen())) return;

        boolean isRestrictedSlot = state.map(RestrictionState::targetItem)
                .map(info -> info.slotIndex() == event.slotId)
                .orElse(false);

        if (!isRestrictedSlot) return;

        if (isRestricted && clicks < getClicksOverride()) {
            clicks++;
            PlayerActionUtil.notifyAll(getMessage(state.get()));
            event.cancel();
        }
    }

    public abstract boolean inCorrectScreen();

    protected String getMessage(T state) {
        StringBuilder message = new StringBuilder(getMessagePrefix());

        for (RestrictionControl<?> control : state.triggeredRestrictors()) {
            message.append(" ").append(control.describeRule());
        }

        message.append(" (Safety Clicks Left: ").append(getClicksOverride() - getClicks()).append(")");
        return message.toString();
    }
}