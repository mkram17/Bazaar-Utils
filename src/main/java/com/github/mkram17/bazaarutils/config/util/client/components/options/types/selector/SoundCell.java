package com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector;

import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.AudioSource;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.SoundsRepo;
import com.teamresourceful.resourcefulconfig.client.components.base.BaseWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SoundCell extends BaseWidget {

    private static final int PLAY_ZONE_WIDTH = 14;
    private static final int DIVIDER_GAP = 4;
    private static final int TEXT_PADDING = 2;

    private static final float PREVIEW_VOLUME = 1.0F;
    private static final float PREVIEW_PITCH = 1.0F;

    private final SoundEvent sound;
    private final String fullId;
    private final String label;
    private final boolean selected;
    private final Runnable onSelect;

    public SoundCell(int x, int y, int width, int height, SoundEvent sound, boolean selected, Runnable onSelect) {
        super(width, height);
        setPosition(x, y);
        this.sound = sound;
        this.fullId = SoundsRepo.identify(sound);
        this.label = fullId.replaceFirst("^minecraft:", "");
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean overPlay = isHovered() && (mouseX - getX()) < PLAY_ZONE_WIDTH;

        if (selected) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x8800AA00);
        } else if (isHovered() && overPlay) {
            context.fill(getX(), getY(), getX() + PLAY_ZONE_WIDTH, getY() + getHeight(), 0x4055FF55);
            context.fill(getX() + PLAY_ZONE_WIDTH, getY(), getX() + getWidth(), getY() + getHeight(), 0x40FFFFFF);
        } else if (isHovered()) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x40FFFFFF);
        }

        // divider between the preview zone and the label, so the two click regions read as distinct even at rest
        context.fill(getX() + PLAY_ZONE_WIDTH - 1, getY() + 2, getX() + PLAY_ZONE_WIDTH, getY() + getHeight() - 2, 0x40FFFFFF);

        Font font = Minecraft.getInstance().font;
        int textY = getY() + (getHeight() - font.lineHeight) / 2;

        context.drawString(font, "▶", getX() + TEXT_PADDING, textY, overPlay ? 0xFFFFFF55 : 0xFFAAAAAA, false);

        String clipped = font.plainSubstrByWidth(label, getWidth() - PLAY_ZONE_WIDTH - DIVIDER_GAP - TEXT_PADDING);
        context.drawString(font, clipped, getX() + PLAY_ZONE_WIDTH + DIVIDER_GAP, textY, 0xFFE0E0E0, false);

        if (isHovered()) {
            context.setComponentTooltipForNextFrame(font, List.of(Component.literal(fullId)), mouseX, mouseY);
        }

        this.applyCursor(context);
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent event, boolean doubled) {
        if (event.x() - getX() < PLAY_ZONE_WIDTH) {
            PlayerLogger.playSound(sound, PREVIEW_VOLUME, PREVIEW_PITCH, AudioSource.UI);
        } else {
            onSelect.run();
        }
    }
}