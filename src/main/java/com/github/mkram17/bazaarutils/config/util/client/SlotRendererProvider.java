package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.api.SlotElement;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigUI;
import net.minecraft.util.Identifier;

public final class SlotRendererProvider {
    private SlotRendererProvider() {}

    public static void register() {
        ResourcefulConfigUI.registerElementRenderer(
                Identifier.of("bazaarutils", "slot"),
                element -> {
                    SlotElement se = SlotElement.wrap(element);
                    return se != null ? new SlotRenderer(se) : null;
                }
        );

        registerElementProviders();
    }

    private static void registerElementProviders() {}
}