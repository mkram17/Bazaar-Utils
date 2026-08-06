package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.bazaar.InputHelper;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;

import java.util.List;

/**
 * Feeds the container/item/click lifecycle to every configured helper of one kind.
 *
 * <p>Helpers are read fresh on each event rather than held: the list they come from is a config
 * entry, so the player can add, remove, or re-slot a button while the game is running.</p>
 *
 * <p>Subscriptions are declared here with {@code inherited = true} — the bus otherwise only sees
 * methods on the instance's own class. See {@code EVENTS_AND_HANDLERS.md}.</p>
 */
public abstract class InputHelperDispatcher extends BUListener {
    protected abstract List<? extends InputHelper<?>> helpers();

    @Subscription(priority = Priority.HIGH, inherited = true)
    @OnlyOnSkyBlock
    public void onContainerLoaded(ContainerLoadedEvent event) {
        helpers().forEach(helper -> helper.onContainerLoaded(event));
    }

    @Subscription(inherited = true)
    @OnlyOnSkyBlock
    public void onReplaceItem(ReplaceItemEvent event) {
        helpers().forEach(helper -> helper.onReplaceItem(event));
    }

    @Subscription(inherited = true)
    @OnlyOnSkyBlock
    public void onSlotClicked(SlotClickEvent event) {
        helpers().forEach(helper -> helper.onSlotClicked(event));
    }
}
