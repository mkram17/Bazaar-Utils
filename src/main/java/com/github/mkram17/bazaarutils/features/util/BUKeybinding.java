package com.github.mkram17.bazaarutils.features.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public abstract class BUKeybinding {
    protected final KeyMapping keyBinding;

    public BUKeybinding(KeyMapping keyBinding) {
        this.keyBinding = keyBinding;
        registerKeybinding(keyBinding);
        registerOnPressed();
    }

    private static void registerKeybinding(KeyMapping keyBinding){
        KeyBindingHelper.registerKeyBinding(keyBinding);
    }

    protected void registerOnPressed(){}

    public String getUsage(){
        return keyBinding.saveString();
    }
}
