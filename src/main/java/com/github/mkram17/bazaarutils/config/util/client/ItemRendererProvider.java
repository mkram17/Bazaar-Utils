package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.api.ItemElement;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigUI;
import net.minecraft.resources.Identifier;

public final class ItemRendererProvider {
    private ItemRendererProvider() {}

    public static void register() {
        ResourcefulConfigUI.registerElementRenderer(
                Identifier.fromNamespaceAndPath("bazaarutils", "item"),
                element -> {
                    ItemElement ie = ItemElement.wrap(element);
                    return ie != null ? new ItemRenderer(ie) : null;
                }
        );
    }
}