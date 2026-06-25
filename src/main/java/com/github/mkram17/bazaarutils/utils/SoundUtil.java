package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.utils.minecraft.sound.AudioSource;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.SoundHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.concurrent.CompletableFuture;

public final class SoundUtil {
    private static final BazaarLogger LOG = BazaarLogger.of(SoundUtil.class);

    private SoundUtil() {}

    public static void playSound(SoundHolder holder) {
        playSound(holder.resolveSound(), holder.getSoundVolume(), holder.getSoundPitch(), holder.getAudioSource());
    }

    public static void playSound(SoundEvent event, float volume, float pitch, AudioSource source) {
        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null) {
            Util.tickExecuteLater(20, () -> playSound(event, volume, pitch, source));

            return;
        }

        SimpleSoundInstance instance = new SimpleSoundInstance(
                event.location(), source.vanilla(),
                volume, pitch,
                SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true
        );

        client.getSoundManager().play(instance);
    }

    public static void playSound(Holder<SoundEvent> holder, float volume, float pitch, AudioSource source) {
        playSound(holder.value(), volume, pitch, source);
    }

    public static void notifyMultipleTimes(int count) {
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < count; i++) {
                Util.tickExecuteLater(1, () -> playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f, AudioSource.AMBIENT));

                try {
                    Thread.sleep(150);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }
}