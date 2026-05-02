package com.github.mkram17.bazaarutils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.concurrent.CompletableFuture;

public class SoundUtil {
    private static final BazaarLogger LOG = BazaarLogger.of(SoundUtil.class);

    public static void playSound(SoundEvent sound, float volume) {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;

        if (client.level == null || player == null || client.getSoundManager() == null) {
//            Util.logError("Failed to play sound due to null value", new Throwable());
            Util.tickExecuteLater(20, () -> playSound(sound, volume));
            return;
        }


        SimpleSoundInstance soundInstance = SimpleSoundInstance.forLocalAmbience(sound, 1f, volume);

        client.getSoundManager().play(soundInstance);
    }
    public static void playSound(Holder<SoundEvent> soundEntry, float volume) {
        Minecraft client = Minecraft.getInstance();

        if (client == null || client.getSoundManager() == null || client.level == null) {
            LOG.warn("Sound playback deferred — level or sound manager not ready");
            Util.tickExecuteLater(20, () -> playSound(soundEntry, volume));
            return;
        }

        SimpleSoundInstance soundInstance = SimpleSoundInstance.forLocalAmbience(soundEntry.value(), 1f, volume);

        client.getSoundManager().play(soundInstance);
    }

    public static void notifyMultipleTimes(int notifyNum){
        CompletableFuture.runAsync(() ->{
            for(int i = 0; i < notifyNum; i++) {
                Util.tickExecuteLater(1, () -> SoundUtil.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, .5f));
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        }
}
