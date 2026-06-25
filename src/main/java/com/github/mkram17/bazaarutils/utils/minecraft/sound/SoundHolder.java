package com.github.mkram17.bazaarutils.utils.minecraft.sound;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public interface SoundHolder {
    SoundEvent DEFAULT_SOUND = SoundEvents.UI_BUTTON_CLICK.value();
    AudioSource DEFAULT_SOURCE = AudioSource.AMBIENT;
    float DEFAULT_VOLUME = 0.25f;
    float DEFAULT_PITCH = 1.0f;

    SoundRef getSoundRef();

    default AudioSource getAudioSource() {
        return DEFAULT_SOURCE;
    }

    default float getSoundVolume() {
        return DEFAULT_VOLUME;
    }

    default float getSoundPitch() {
        return DEFAULT_PITCH;
    }

    default SoundEvent resolveSound() {
        return switch (getSoundRef()) {
            case SoundRef.Direct(SoundEvent event) -> event;
            case SoundRef.ById(var id) -> resolveById(id.get());
        };
    }

    private static SoundEvent resolveById(String rawId) {
        SoundEvent resolved = SoundsRepo.resolve(rawId);
        return resolved != null ? resolved : DEFAULT_SOUND;
    }
}