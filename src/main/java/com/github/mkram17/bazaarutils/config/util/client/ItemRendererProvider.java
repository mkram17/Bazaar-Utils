package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.api.ItemElement;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigUI;
import net.minecraft.resources.Identifier;


@PreInitModule
public final class ItemRendererProvider {
    public ItemRendererProvider() {
        register();
    }

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