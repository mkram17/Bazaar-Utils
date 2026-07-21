package com.github.mkram17.bazaarutils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Version-specific compatibility shims managed by Stonecutter.
 * <p>
 * 26.2 moved the current-screen accessor and mutator off {@link Minecraft} and onto
 * {@code Minecraft.gui}, and replaced the per-color {@code *_STAINED_GLASS_PANE} item
 * constants with a single {@code Items.STAINED_GLASS_PANE} color collection.
 */
public class VersionCompat {
    public static Screen getScreen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen();
        //?} else {
      /*return client.screen;
        *///?}
    }

    public static void setScreen(Minecraft client, Screen screen) {
        //? if >=26.2 {
        client.gui.setScreen(screen);
        //?} else {
      /*client.setScreen(screen);
        *///?}
    }

    public static Item stainedGlassPane(DyeColor color) {
        //? if >=26.2 {
        return Items.STAINED_GLASS_PANE.pick(color);
        //?} else {
      /*return switch (color) {
            case BLUE -> Items.BLUE_STAINED_GLASS_PANE;
            case ORANGE -> Items.ORANGE_STAINED_GLASS_PANE;
            case BLACK -> Items.BLACK_STAINED_GLASS_PANE;
            case GREEN -> Items.GREEN_STAINED_GLASS_PANE;
            case RED -> Items.RED_STAINED_GLASS_PANE;
            default -> Items.PURPLE_STAINED_GLASS_PANE;
        };
        *///?}
    }
}
