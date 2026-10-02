package com.github.mkram17.bazaarutils.events.minecraft;

import lombok.Getter;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Posted after SignEditScreen initialization; excludes hanging sign screens.
 *
 * @see SignEditScreen
 * @see com.github.mkram17.bazaarutils.mixin.MixinSignEditScreen
 */
public final class SignOpenEvent extends SkyBlockEvent {
    /**
     * The sign editing screen being opened.
     */
    @Getter
    private final SignEditScreen screen;

    /**
     * Creates a new SignOpenEvent.
     *
     * @param screen the sign editing screen being opened
     */
    public SignOpenEvent(SignEditScreen screen) {
        this.screen = screen;
    }
}