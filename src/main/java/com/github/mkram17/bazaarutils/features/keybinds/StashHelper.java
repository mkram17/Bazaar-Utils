package com.github.mkram17.bazaarutils.features.keybinds;

//? if > 1.21.8 {
import com.github.mkram17.bazaarutils.BazaarUtils;
import net.minecraft.resources.Identifier;
//?}
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import lombok.Getter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

public class StashHelper {
    @Getter
    private static int ticksBetweenPresses;
    //? if > 1.21.8
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse(BazaarUtils.MODID));
    private static final KeyMapping keyBinding = new KeyMapping(
       "Pick Up Stash",
       //? if >=26.3 {
       InputConstants.Type.KEYBOARD,
       //?} else {
       /*InputConstants.Type.KEYSYM,
       *///?}
       InputConstants.KEY_V,
       //? if > 1.21.8 {
       CATEGORY
       //?} else {
     /*"Bazaar Utils"
        *///?}
    );

    @RunOnInit
    public static void initializeKeybind(){
        KeyMappingHelper.registerKeyMapping(keyBinding);
    }

    @RunOnInit
    public static void registerOnPressed(){
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticksBetweenPresses++;
            if(!keyBinding.isDown()) {
                return;
            }
            if(ticksBetweenPresses > 10) {
                ticksBetweenPresses = 0;
                GUIUtils.closeHandledScreen();
                PlayerActionUtil.runCommand("pickupstash");
            }
        });
    }

    public static String getUsage(){
        return keyBinding.saveString();
    }
}
