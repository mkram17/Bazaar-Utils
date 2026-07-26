package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.SlotInteractionEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.events.predicates.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import lombok.Getter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

import java.util.List;
import java.util.Optional;

public abstract class RestrictionHelper<T extends RestrictionHelper.RestrictionState> extends BUListener implements ToggleableFeature, ScreenConstrained {
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

    protected abstract Optional<T> makeState(ContainerLoadedEvent event);

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

    @Subscription(inherited = true)
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    public void onContainerLoaded(ContainerLoadedEvent event) {
        if (!inCorrectScreen(event)) {
            resetState();
            return;
        }

        state = makeState(event);
        isRestricted = state.map(state -> !state.triggeredRestrictors().isEmpty()).orElse(true);
        clicks = 0;
    }

    // Subscribes to the local SlotInteractionEvent rather than SkyblockAPI's mouse-only
    // SlotClickEvent so keyboard-driven interactions (hotbar number keys, drop key, ...) are also
    // gated — the whole point of the restriction is to prevent accidental irreversible sells.
    @Subscription(inherited = true)
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstraintsInterface = true)
    public void onSlotClicked(SlotInteractionEvent event) {
        // ItemInfo.slotIndex() is a container index (see SellablePageLayout#getSlot), so it is
        // correct to compare against getContainerSlot(); but player-inventory slots share that
        // index space (0-35) and would trip the restriction on unrelated clicks. Exclude them.
        boolean isRestrictedSlot = !event.isInPlayerInventory() && state.map(RestrictionState::targetItem)
                .map(info -> info.slotIndex() == event.getSlot().getContainerSlot())
                .orElse(false);

        if (!isRestrictedSlot) return;

        if (isRestricted && clicks < getClicksOverride()) {
            clicks++;
            PlayerActionUtil.notifyAll(getMessage(state.get()));
            event.cancel();
        }
    }

    protected String getMessage(T state) {
        StringBuilder message = new StringBuilder(getMessagePrefix());

        for (RestrictionControl<?> control : state.triggeredRestrictors()) {
            message.append(" ").append(control.describeRule());
        }

        message.append(" (Safety Clicks Left: ").append(getClicksOverride() - getClicks()).append(")");
        return message.toString();
    }
}