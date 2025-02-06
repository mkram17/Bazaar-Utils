package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.OutdatedItemEvent;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

//TODO change the message number instead of sending more
public class AutoOpen {
    @EventHandler
    public void onOutdated(OutdatedItemEvent e){
        if(!BUConfig.get().autoOpenBazaar || BazaarUtils.gui.inBazaar())
            return;
        CompletableFuture.runAsync(() ->{
            for(int i = 3; i >= 1; i--) {
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

            GUIUtils.sendCommand("bz");
        });
    }
    public static Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Auto Open Bazaar"))
                .description(OptionDescription.of(Text.literal("Automatically open the bazaar after a delay when an order becomes outdated.")))
                .binding(false,
                        BUConfig.get()::isAutoOpenBazaar,
                        BUConfig.get()::setAutoOpenBazaar)
                .controller(BUConfig::createBooleanController)
                .build();
    }
}
