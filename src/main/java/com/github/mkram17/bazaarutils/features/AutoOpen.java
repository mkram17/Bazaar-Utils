package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.OutdatedItemEvent;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import meteordevelopment.orbit.EventHandler;

import java.util.concurrent.CompletableFuture;

public class AutoOpen {
    @EventHandler
    public void onOutdated(OutdatedItemEvent e){
        if(!BUConfig.autoOpenBazaar || BazaarUtils.gui.inBazaar())
            return;
        CompletableFuture.runAsync(() ->{
            for(int i = 3; i >= 1; i++) {
                try {
                    if(i == 3)
                        Util.notifyAll("Opening bazaar in 3");
                    else
                        Util.notifyAll(String.valueOf(i));
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }

            GUIUtils.openBazaar();
        });
    }
}
