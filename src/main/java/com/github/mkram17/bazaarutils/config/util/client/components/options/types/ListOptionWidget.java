package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.config.util.api.SerializableList;
import com.github.mkram17.bazaarutils.config.util.client.SerializableListScreen;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigObjectEntry;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ObjectOptionWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import org.jetbrains.annotations.NotNull;

public class ListOptionWidget<T> extends ObjectOptionWidget {
    private static final int WIDTH = 81;

    private final SerializableList<T> list;

    public ListOptionWidget(ResourcefulConfigObjectEntry entry, SerializableList<T> list) {
        super(entry);
        this.setWidth(WIDTH);
        this.list = list;
    }

    @Override
    public void onClick(@NotNull Click event, boolean bl) {
        MinecraftClient.getInstance().setScreen(
                new SerializableListScreen<>(MinecraftClient.getInstance().currentScreen, list)
        );
    }
}