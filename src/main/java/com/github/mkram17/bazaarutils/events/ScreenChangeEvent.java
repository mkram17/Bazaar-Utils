package com.github.mkram17.bazaarutils.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.screens.Screen;

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
 * @EventHandler
 * public void onScreenChange(ScreenChangeEvent event) {
 *     Screen old = event.getOldScreen();
 *     Screen new = event.getNewScreen();
 *     // Handle screen transition
 * }
 * }
 * </pre>
 * @see com.github.mkram17.bazaarutils.mixin.MinecraftMixin
 */
@AllArgsConstructor
public class ScreenChangeEvent {
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
}
