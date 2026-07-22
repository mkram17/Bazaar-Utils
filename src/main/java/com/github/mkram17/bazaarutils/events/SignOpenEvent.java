package com.github.mkram17.bazaarutils.events;

import lombok.Getter;
import meteordevelopment.orbit.ICancellable;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

public class SignOpenEvent implements ICancellable {
    @Getter
    private final AbstractSignEditScreen signEditScreen;
    private boolean cancelled;

    public SignOpenEvent(AbstractSignEditScreen signEditScreen) {
        this.signEditScreen = signEditScreen;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}