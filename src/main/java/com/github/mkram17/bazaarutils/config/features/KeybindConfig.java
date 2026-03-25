package com.github.mkram17.bazaarutils.config.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.features.keybinds.StashHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeybindConfig {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse(BazaarUtils.MOD_ID));

    //Keybinds get registered on object creation.
    public StashHelper stashHelper = new StashHelper(new KeyMapping(
            "Pick Up Stash",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    ));
}
