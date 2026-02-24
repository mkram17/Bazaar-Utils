package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import com.teamresourceful.resourcefulconfig.client.components.base.SpriteButton;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * A pre-configured remove {@link SpriteButton} matching RC's native button style
 * (12x12, 2px padding, CLOSE sprite, CLOSE tooltip).
 */
public final class RemoveOptionWidget {
    public static final Text REMOVE = Text.translatable("bazaarutils.rconfig.ui.constant.remove");

    private RemoveOptionWidget() {}

    public static ClickableWidget of(Runnable onPress) {
        return SpriteButton.builder(12, 12)
                .padding(2)
                .sprite(ModSprites.DELETE)
                .tooltip(REMOVE)
                .onPress(onPress)
                .build();
    }
}