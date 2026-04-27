package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class ScreenContext {
    private final Screen screen;

    private final ScreenType type;

    public ScreenContext(ScreenManager.ScreenSnapshot snapshot) {
        this.screen = snapshot.screen();
        this.type = snapshot.type();
    }

    public ScreenContext(Screen screen, @Nullable ScreenType type) {
        this.screen = screen;
        this.type = type;
    }

    public Screen screen() {
        return screen;
    }

    public Optional<ScreenType> type() {
        return Optional.ofNullable(type);
    }

    public boolean is(@NotNull ScreenType wanted) {
        return type == wanted;
    }

    public <T extends AbstractContainerScreen<?>> Optional<T> as(Class<T> type) {
        return type.isInstance(screen)
                ? Optional.of(type.cast(screen))
                : Optional.empty();
    }

    public <T extends AbstractContainerScreen<?>> Optional<T> asIf(ScreenType wanted, Class<T> screenClass) {
        if (!is(wanted)) return Optional.empty();

        return as(screenClass);
    }

    public boolean isAnyOf(ScreenType... wanted) {
        if (type == null) {
            return false;
        }

        for (ScreenType type : wanted) {
            if (type == this.type) {
                return true;
            }
        }

        return false;
    }
}
