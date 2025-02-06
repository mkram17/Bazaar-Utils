package com.github.mkram17.bazaarutils.Events;

import lombok.Getter;
import meteordevelopment.orbit.ICancellable;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;

public class SignOpenEvent implements ICancellable {
    @Getter
    private final SignEditScreen signEditScreen;
    private boolean cancelled;

    public SignOpenEvent(SignEditScreen signEditScreen) {
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