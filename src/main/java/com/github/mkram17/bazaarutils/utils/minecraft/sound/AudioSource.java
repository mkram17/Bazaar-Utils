package com.github.mkram17.bazaarutils.utils.minecraft.sound;

import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import lombok.AllArgsConstructor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundSource;

/**
 * Mod-facing mirror of vanilla {@link SoundSource}. Kept as its own enum rather
 * than exposing SoundSource directly in config, gives ResourcefulConfig a closed, renderer-friendly
 * set, and decouples us from vanilla ever renaming/reordering categories.
 */
@AllArgsConstructor
public enum AudioSource implements Translatable, TooltipProvider {
    UI(SoundSource.UI),
    MASTER(SoundSource.MASTER),
    PLAYERS(SoundSource.PLAYERS),
    AMBIENT(SoundSource.AMBIENT),
    VOICE(SoundSource.VOICE),
    BLOCKS(SoundSource.BLOCKS),
    HOSTILE(SoundSource.HOSTILE),
    NEUTRAL(SoundSource.NEUTRAL),
    WEATHER(SoundSource.WEATHER),
    RECORDS(SoundSource.RECORDS),
    MUSIC(SoundSource.MUSIC);

    private final SoundSource vanilla;

    public SoundSource vanilla() {
        return vanilla;
    }

    @Override
    public String getTranslationKey() {
        return "bazaarutils.config.audio_source." + name().toLowerCase() + ".label";
    }

    @Override
    public MutableComponent getTooltip() {
        return Component.translatable("bazaarutils.config.audio_source." + name().toLowerCase() + ".hint");
    }
}