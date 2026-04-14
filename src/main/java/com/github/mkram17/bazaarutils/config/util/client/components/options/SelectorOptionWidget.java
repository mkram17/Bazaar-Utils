package com.github.mkram17.bazaarutils.config.util.client.components.options;

import com.teamresourceful.resourcefulconfig.client.components.base.SpriteButton;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ResetableWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public abstract class SelectorOptionWidget extends SpriteButton implements ResetableWidget {
    protected static final Component SELECT = Component.translatable("bazaarutils.rconfig.ui.constant.select");

    protected SelectorOptionWidget(Identifier sprite, Component tooltip) {
        super(12, 12, 2, sprite, () -> {}, tooltip);
    }

    @Override
    public void reset() {}
}