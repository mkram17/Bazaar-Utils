package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.api.SoundElement;
import com.github.mkram17.bazaarutils.config.util.client.components.options.ResetOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.SoundOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.SoundStringOptionWidget;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.SoundsRepo;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigElementRenderer;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigValueEntry;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public record SoundRenderer(SoundElement element) implements ResourcefulConfigElementRenderer {
    @Override
    public Component title() {
        return element.title();
    }

    @Override
    public Component description() {
        return element.description();
    }

    @Override
    public List<AbstractWidget> widgets() {
        ResourcefulConfigValueEntry entry = element.valueEntry();
        List<SoundEvent> sounds = SoundsRepo.getSounds(element.tags());

        SoundOptionWidget soundWidget = new SoundOptionWidget(sounds, entry::getString, entry::setString);

        SoundStringOptionWidget stringWidget = new SoundStringOptionWidget(
                entry::getString,
                s -> {
                    if (s.isBlank()) {
                        entry.setString(s);
                        return true;
                    }

                    SoundEvent resolved = SoundsRepo.resolve(s);
                    if (resolved == null) return false;
                    if (sounds.stream().noneMatch(sound -> SoundsRepo.identify(sound).equals(SoundsRepo.identify(resolved)))) return false;

                    entry.setString(s);
                    return true;
                }
        );

        return List.of(
                soundWidget,
                stringWidget,
                ResetOptionWidget.of(() -> {
                    entry.reset();
                    stringWidget.reset();
                })
        );
    }
}