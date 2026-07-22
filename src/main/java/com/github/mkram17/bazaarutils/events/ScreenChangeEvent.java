package com.github.mkram17.bazaarutils.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.screens.Screen;

@AllArgsConstructor
public class ScreenChangeEvent {
    @Getter @Setter
    private Screen oldScreen;
    @Getter @Setter
    private Screen newScreen;
}
