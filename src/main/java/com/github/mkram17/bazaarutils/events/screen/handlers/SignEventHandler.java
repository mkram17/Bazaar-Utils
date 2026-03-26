package com.github.mkram17.bazaarutils.events.screen.handlers;

import com.github.mkram17.bazaarutils.events.screen.SignOpenEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenInitializedEvent;

@Module
public class SignEventHandler extends BUListener {
    @Subscription
    public void onScreenInit(ScreenInitializedEvent event) {
        if (!(event.getScreen() instanceof SignEditScreen signEditScreen)) return;

        new SignOpenEvent(signEditScreen).post(SkyBlockAPI.getEventBus());
    }
}