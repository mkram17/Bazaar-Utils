package com.github.mkram17.bazaarutils.events.minecraft;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.screens.Screen;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

/**
 * Event fired when the player's current screen changes.
 * <p>
 * This event is triggered whenever the player transitions from one screen to another,
 * such as opening or closing a GUI, switching between different menus, or changing screens.
 * </p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * {@code
 * @Subscription
 * public void onScreenChange(ScreenChangeEvent event) {
 *     Screen old = event.getOldScreen();
 *     Screen new = event.getNewScreen();
 *     // Handle screen transition
 * }
 * }
 * </pre>
 * @see com.github.mkram17.bazaarutils.mixin.MinecraftMixin
 */
public abstract class ScreenChangeEvent extends SkyBlockEvent {
    /**
     * The screen that was previously displayed.
     * May be null if no screen was open before.
     */
    @Getter @Setter
    private Screen oldScreen;
    
    /**
     * The screen that is now being displayed.
     * May be null if the screen is being closed.
     */
    @Getter @Setter
    private Screen newScreen;

    public ScreenChangeEvent(Screen oldScreen, Screen newScreen) {
        this.oldScreen = oldScreen;
        this.newScreen = newScreen;
    }

    public static final class Pre extends ScreenChangeEvent {
        public Pre(Screen oldScreen, Screen newScreen) {
            super(oldScreen, newScreen);
        }
    }

    public static final class Post extends ScreenChangeEvent {
        public Post(Screen oldScreen, Screen newScreen) {
            super(oldScreen, newScreen);
        }
    }
}
