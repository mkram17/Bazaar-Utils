package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import com.teamresourceful.resourcefulconfig.client.components.base.SpriteButton;
import net.minecraft.client.gui.widget.ClickableWidget;

/**
 * A pre-configured reset {@link SpriteButton} matching RC's native reset button style
 * (12x12, 2px padding, RESET sprite, RESET tooltip).
 */
public final class ResetOptionWidget {

    private ResetOptionWidget() {}

    public static ClickableWidget of(Runnable onPress) {
        return SpriteButton.builder(12, 12)
                .padding(2)
                .sprite(ModSprites.RESET)
                .tooltip(UIConstants.RESET)
                .onPress(onPress)
                .build();
    }
}