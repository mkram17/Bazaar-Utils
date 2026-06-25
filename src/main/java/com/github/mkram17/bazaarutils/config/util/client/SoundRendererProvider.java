package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.api.SoundElement;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigUI;
import net.minecraft.resources.Identifier;

public final class SoundRendererProvider {
    private SoundRendererProvider() {}

    public static void register() {
        ResourcefulConfigUI.registerElementRenderer(
                Identifier.fromNamespaceAndPath("bazaarutils", "sound"),
                element -> {
                    SoundElement se = SoundElement.wrap(element);

                    return se != null ? new SoundRenderer(se) : null;
                }
        );
    }
}