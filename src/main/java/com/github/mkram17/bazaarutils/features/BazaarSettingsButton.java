package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.misc.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.misc.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.mixin.AccessorAbstractContainerScreen;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.VersionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;

public class BazaarSettingsButton {
    private static final Identifier BASE = Identifier.tryBuild(BazaarUtils.MODID, "widget/settings_widget_base");
    private static final Identifier HOVER = Identifier.tryBuild(BazaarUtils.MODID, "widget/settings_widget_hover");
    public static final WidgetSprites SLOT_BUTTON_TEXTURES = new WidgetSprites(
            BASE,
            HOVER);

    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidget() {
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        if (!(VersionCompat.getScreen(Minecraft.getInstance()) instanceof AccessorAbstractContainerScreen screen) || !screenInfo.inBazaar())
            return Collections.emptyList();

        String screenTitle = VersionCompat.getScreen(Minecraft.getInstance()).getTitle().getString();

        ItemSlotButtonWidget.ScreenWidgetDimensions dimensions = ItemSlotButtonWidget.getSafeScreenDimensions(screen, screenTitle);

        int buttonSize = 18;
        int spacing = 4;
        int buttonX = dimensions.x() - buttonSize - spacing;
        int currentButtonY = dimensions.y() + spacing;


        ItemSlotButtonWidget button = new ItemSlotButtonWidget(
                buttonX,
                currentButtonY,
                buttonSize, buttonSize,
                SLOT_BUTTON_TEXTURES,
                (btn) -> {
                    VersionCompat.setScreen(Minecraft.getInstance(), BUConfig.get().createGUI(VersionCompat.getScreen(Minecraft.getInstance())));
                },
                null,
                Component.literal("Bazaar Utils Settings")
        );
        return Collections.singletonList(button);
    }

}
