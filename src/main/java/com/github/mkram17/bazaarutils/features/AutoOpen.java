package com.github.sirmegabite.bazaarutils.features;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.EventHandlers.OutdatedItemEvent;
import com.github.sirmegabite.bazaarutils.Utils.GUIUtils;
import com.github.sirmegabite.bazaarutils.Utils.Util;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.concurrent.CompletableFuture;

public class AutoOpen {
    @SubscribeEvent
    public void onOutdated(OutdatedItemEvent e){
        if(!BUConfig.autoOpenBazaar || BazaarUtils.gui.inBazaar())
            return;
        CompletableFuture.runAsync(() ->{
            for(int i = 3; i >= 1; i++) {
                try {
                    Thread.sleep(1000);
                    if(i == 3)
                        Util.notifyAll("Opening bazaar in 3");
                    else
                        Util.notifyAll(String.valueOf(i));
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
            GUIUtils.openBazaar();
        });
    }
}
