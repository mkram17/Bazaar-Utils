package com.github.mkram17.bazaarutils.events.minecraft;

import lombok.Getter;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;

/**
 * Event fired when a sign editing screen is opened.
 * <p>
 * This event is triggered when the player opens a sign editing interface, typically when
 * interacting with bazaar order creation or other sign-based input systems.
 * </p>
 *
 * 
 * @see SignEditScreen
 * @see com.github.mkram17.bazaarutils.mixin.MixinSignOpen
 */
public class SignOpenEvent {
    /**
     * The sign editing screen being opened.
     */
    @Getter
    private final SignEditScreen signEditScreen;

    /**
     * Creates a new SignOpenEvent.
     *
     * @param signEditScreen the sign editing screen being opened
     */
    public SignOpenEvent(SignEditScreen signEditScreen) {
        this.signEditScreen = signEditScreen;
    }
}