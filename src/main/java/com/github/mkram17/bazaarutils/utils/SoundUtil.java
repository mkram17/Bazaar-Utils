package com.github.mkram17.bazaarutils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.concurrent.CompletableFuture;

public class SoundUtil {
    /**
     * Plays {@code sound} locally, retrying in a second if the client is not in a world yet.
     * A sound requested during load is normal, not an error, so nothing is logged for it.
     */
    public static void playSound(SoundEvent sound, float volume) {
        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null || client.getSoundManager() == null) {
            Util.tickExecuteLater(20, () -> playSound(sound, volume));
            return;
        }

        client.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(sound, 1f, volume));
    }

    public static void playSound(Holder<SoundEvent> soundEntry, float volume) {
        playSound(soundEntry.value(), volume);
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
