package com.github.mkram17.bazaarutils.events.minecraft;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import lombok.Getter;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Posted on the client thread when chest loading heuristics pass: a nonempty final slot
 * and no recognised Loading markers. Polling stops after 50 retries or a screen change.
 * Slot lists retain empty slots and separate container slots from player-inventory slots.
 */
@Getter
public final class ContainerLoadedEvent extends SkyBlockEvent {
    /**
     * The container that is being displayed.
     */
    private final AbstractContainerScreen<ChestMenu> screen;

    private final @Nullable ScreenType type;

    /**
     * The resolved {@link ScreenType} for this container, or empty if unrecognised.
     */
    public Optional<ScreenType> getType() {
        return Optional.ofNullable(type);
    }

    /**
     * The inventory of the container opened.
     */
    private final Container container;

    /**
     * The display name of the container.
     */
    private final String title;

    /**
     * The title of the container as a {@link Component}, preserving formatting.
     */
    private final Component titleComponent;

    /**
     * All slots in the container, exluding the player's inventory slots.
     */
    private List<Slot> containerSlots;

    /**
     * All slots belonging to the player's inventory within this container screen.
     */
    private List<Slot> playerSlots;

    public ScreenContext asContext() {
        return new ScreenContext(screen, type);
    }

    public ContainerLoadedEvent(
            AbstractContainerScreen<ChestMenu> screen,
            @Nullable ScreenType type,
            Container container,
            Component titleComponent,
            List<Slot> containerSlots,
            List<Slot> playerSlots) {
        this.screen = screen;
        this.type = type;
        this.container = container;
        this.titleComponent = titleComponent;
        this.title = Util.removeFormatting(titleComponent.getString());
        this.containerSlots = containerSlots;
        this.playerSlots = playerSlots;
    }
}