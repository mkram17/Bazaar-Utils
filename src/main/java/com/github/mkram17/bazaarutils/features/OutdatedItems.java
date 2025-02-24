package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.OutdatedItemEvent;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

//TODO change the message number instead of sending more
public class OutdatedItems {
    @Getter @Setter
    private boolean autoOpenEnabled;
    @Getter @Setter
    private boolean notifyOutdated;
    @Getter @Setter
    private int outdatedTiming;

    public OutdatedItems(boolean autoOpenEnabled, boolean notifyOutdated, int outdatedTiming) {
        this.autoOpenEnabled = autoOpenEnabled;
        this.notifyOutdated = notifyOutdated;
        this.outdatedTiming = outdatedTiming;
    }

    @EventHandler
    public void onOutdated(OutdatedItemEvent e){
        if(notifyOutdated)
            Util.notifyChatCommand("Your order for " + e.getItem().getVolume() + "x " + e.getItem().getName() + " is now outdated.", "bz");
        if(BazaarUtils.gui.inBazaar() || !autoOpenEnabled)
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
    public Collection<Option<Boolean>> createOptions() {
        ArrayList<Option<Boolean>> options = new ArrayList<>();
        options.add(Option.<Boolean>createBuilder()
                .name(Text.literal("Open Bazaar on Outdated Orders"))
                .description(OptionDescription.of(Text.literal("Automatically open the bazaar after a delay when an order becomes outdated.")))
                .binding(false,
                        this::isAutoOpenEnabled,
                        this::setAutoOpenEnabled)
                .controller(BUConfig::createBooleanController)
                .build());
        options.add(Option.<Boolean>createBuilder()
                .name(Text.literal("Notify on Outdated Orders"))
                .description(OptionDescription.of(Text.literal("Sends a message in chat when someone has undercut your order.")))
                .binding(true,
                        this::isNotifyOutdated,
                        this::setNotifyOutdated)
                .controller(BUConfig::createBooleanController)
                .build());
        return options;
    }
}
