package com.github.mkram17.bazaarutils.events.screen;

import lombok.Getter;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Event fired when a sign editing screen is opened.
 * <p>
 * This event is triggered when the player opens a sign editing interface, typically when
 * interacting with bazaar order creation or other sign-based input systems.
 * </p>
 *
 * 
 * @see SignEditScreen
 * @see com.github.mkram17.bazaarutils.events.screen.handlers.SignEventHandler
 */
public class SignOpenEvent extends SkyBlockEvent {
    @Getter
    private final SignEditScreen signEditScreen;

    public SignOpenEvent(SignEditScreen signEditScreen) {
        this.signEditScreen = signEditScreen;
    }
}