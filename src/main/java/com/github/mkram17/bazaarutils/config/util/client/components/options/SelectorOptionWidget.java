package com.github.mkram17.bazaarutils.config.util.client.components.options;

import com.teamresourceful.resourcefulconfig.client.components.base.SpriteButton;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ResetableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public abstract class SelectorOptionWidget extends SpriteButton implements ResetableWidget {
    protected static final Text SELECT = Text.translatable("bazaarutils.rconfig.ui.constant.select");

    protected SelectorOptionWidget(Identifier sprite, Text tooltip) {
        super(12, 12, 2, sprite, () -> {}, tooltip);
    }

    @Override
    public void reset() {}
}