package com.github.mkram17.bazaarutils.events.minecraft;

import lombok.Getter;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.CancellableSkyBlockEvent;

/**
 * Cancellable event fired for <strong>every</strong> slot interaction on an
 * {@link AbstractContainerScreen}, posted from {@code AbstractContainerScreen#slotClicked} at HEAD.
 *
 * <p>
 * SkyblockAPI's {@code SlotClickEvent} is posted only from {@code ScreenMouseClickEvent}, keyed off
 * the hovered slot, so it is mouse-only. Vanilla routes many other interactions through
 * {@code slotClicked} — number-key hotbar swaps, the drop key, shift-click, double-click — none of
 * which fire a mouse click. This event covers all of those paths.
 * </p>
 *
 * <p>
 * It exists specifically so safety features such as {@code RestrictionHelper} can gate keyboard-driven
 * sells; cosmetic consumers (bookmark toggles, price charts, input helpers) stay on SkyblockAPI's
 * mouse-only {@code SlotClickEvent}, for which mouse coverage is sufficient.
 * </p>
 *
 * @see Slot
 * @see ContainerInput
 */
@Getter
public class SlotInteractionEvent extends CancellableSkyBlockEvent {
    /**
     * The container screen the interaction occurred on.
     */
    @NotNull
    private final AbstractContainerScreen<?> screen;

    /**
     * The slot that was interacted with.
     */
    @NotNull
    private final Slot slot;

    /**
     * The vanilla menu slot id ({@link Slot#index}), as passed to {@code slotClicked}.
     */
    private final int slotId;

    /**
     * The mouse button, or — for {@link ContainerInput#SWAP} — the target hotbar index.
     */
    private final int button;

    /**
     * The type of interaction vanilla resolved this click to.
     */
    @NotNull
    private final ContainerInput actionType;

    /**
     * Whether the interacted slot belongs to the player's inventory rather than the container.
     * Player-inventory container indices (0-35) overlap low chest indices, so slot-index matches
     * must exclude these.
     */
    private final boolean inPlayerInventory;

    public SlotInteractionEvent(
            @NotNull AbstractContainerScreen<?> screen,
            @NotNull Slot slot,
            int slotId,
            int button,
            @NotNull ContainerInput actionType) {
        this.screen = screen;
        this.slot = slot;
        this.slotId = slotId;
        this.button = button;
        this.actionType = actionType;
        this.inPlayerInventory = slot.container instanceof Inventory;
    }
}
