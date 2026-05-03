package com.github.mkram17.bazaarutils.features.keybinds;

import com.github.mkram17.bazaarutils.config.features.KeybindConfig;
import com.github.mkram17.bazaarutils.events.BUKeybinding;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

@Module
public class StashHelper extends BUKeybinding {

    private static final int COOLDOWN_TICKS = 10;

    private int cooldown = COOLDOWN_TICKS;

    public StashHelper() {
        super(KeybindConfig.STASH_HELPER);
    }

    @Override
    protected void registerFabricEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (cooldown < COOLDOWN_TICKS) {
                cooldown++;
                return;
            }

            if (!keyMapping.isDown()) return;
            cooldown = 0;

            ScreenManager.closeScreen();
            PlayerLogger.runCommand("pickupstash");
        });
    }
}