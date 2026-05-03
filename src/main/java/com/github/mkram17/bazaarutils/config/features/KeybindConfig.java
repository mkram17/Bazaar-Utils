package com.github.mkram17.bazaarutils.config.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class KeybindConfig {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse(BazaarUtils.MOD_ID));

    public static final KeyMapping STASH_HELPER = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "bazaarutils.keybind.stash_helper",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_V,
                    CATEGORY
            )
    );

    public static final KeyMapping DIMMED_EXPAND = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "bazaarutils.keybind.dimmed_expand",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_ALT,
                    CATEGORY
            )
    );

    private KeybindConfig() {}
}