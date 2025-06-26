package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.OutdatedItemEvent;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static com.github.mkram17.bazaarutils.BazaarUtils.eventBus;

//TODO change the message number instead of sending more
public class OutdatedItems implements BUListener {
    @Getter @Setter
    private boolean autoOpenEnabled;
    @Getter @Setter
    private boolean notifyOutdated;
    @Getter @Setter
    private boolean notificationSound;


    public OutdatedItems(boolean autoOpenEnabled, boolean notifyOutdated) {
        this.autoOpenEnabled = autoOpenEnabled;
        this.notifyOutdated = notifyOutdated;
        this.notificationSound = true;
    }

    @EventHandler
    public void onOutdated(OutdatedItemEvent e){
        if(notifyOutdated) {
            Text amount = Text.literal(e.getItem().getVolume() + "x ").formatted(Formatting.BOLD).formatted(Formatting.DARK_PURPLE);
            Text itemName = Text.literal(e.getItem().getName().formatted(Formatting.BOLD).formatted(Formatting.GOLD));
            MutableText message = Text.literal("[Bazaar Utils] ").formatted(Formatting.GOLD)
                    .append(Text.literal("Your " + e.getItem().getPriceInfo().getPriceType().getString().toLowerCase() + " order for ").formatted(Formatting.WHITE))
                    .append(amount)
                    .append(itemName)
                    .append(Text.literal( " is now outdated.").formatted(Formatting.WHITE))
                    .append(Text.literal(" Click for /bz").formatted(Formatting.GOLD));


            Util.tickExecuteLater(2, () -> {
                if(BUConfig.get().developerMode) {
                    message.append(Text.literal(". Market Price: " + e.getItem().getPriceInfo().getMarketPrice() + " Order Price: " + e.getItem().getPriceInfo().getPrice()));
                    Util.notifyChatCommand(message, "bz");
                } else
                    Util.notifyChatCommand(message, "bz");
            });
            if(notificationSound)
                SoundUtil.notifyMultipleTimes(3);
        }
        if(GUIUtils.inBazaar() || !autoOpenEnabled)
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

            Util.sendCommand("bz");
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
        options.add(Option.<Boolean>createBuilder()
                .name(Text.literal("Sound for Outdated Orders"))
                .description(OptionDescription.of(Text.literal("Plays three short notification sounds when your order becomes outdated.")))
                .binding(true,
                        this::isNotificationSound,
                        this::setNotificationSound)
                .controller(BUConfig::createBooleanController)
                .build());
        return options;
    }

    @Override
    public void subscribe() {
        eventBus.subscribe(this);
    }
}
