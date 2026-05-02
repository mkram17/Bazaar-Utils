package com.github.mkram17.bazaarutils.features.keybinds;

import com.github.mkram17.bazaarutils.features.util.BUKeybinding;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import lombok.Getter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;

public class StashHelper extends BUKeybinding {
    @Getter
    private int ticksBetweenPresses;

    public StashHelper(KeyMapping keyBinding) {
        super(keyBinding);
    }

    @Override
    protected void registerOnPressed(){
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticksBetweenPresses++;
            if(!keyBinding.isDown()) {
                return;
            }
            if(ticksBetweenPresses > 10) {
                ticksBetweenPresses = 0;
                ScreenManager.closeScreen();
                PlayerLogger.runCommand("pickupstash");
            }
        });
    }
}
