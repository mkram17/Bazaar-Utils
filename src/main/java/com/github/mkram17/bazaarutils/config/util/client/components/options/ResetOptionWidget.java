package com.github.mkram17.bazaarutils.config.util.client.components.options;

import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import com.teamresourceful.resourcefulconfig.client.components.base.SpriteButton;
import net.minecraft.client.gui.components.AbstractWidget;

public final class ResetOptionWidget {
    private ResetOptionWidget() {}

    public static AbstractWidget of(Runnable onPress) {
        return SpriteButton.builder(12, 12)
                .padding(2)
                .sprite(ModSprites.RESET)
                .tooltip(UIConstants.RESET)
                .onPress(onPress)
                .build();
    }
}