package com.github.mkram17.bazaarutils.utils.minecraft.sound;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

public sealed interface SoundRef {
    record Direct(SoundEvent event) implements SoundRef {}
    record ById(Supplier<String> id) implements SoundRef {}

    static SoundRef of(SoundEvent event) {
        return new Direct(event);
    }

    static SoundRef of(Holder<SoundEvent> event) {
        return new Direct(event.value());
    }

    static SoundRef of(String id) {
        return new ById(() -> id);
    }

    static SoundRef of(Supplier<String> id) {
        return new ById(id);
    }
}