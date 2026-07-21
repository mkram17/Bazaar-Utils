package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.misc.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.misc.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.mixin.AccessorAbstractContainerScreen;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.VersionCompat;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;

//brings you to the orders page as long as you have a cookie
public class BazaarOpenOrdersButton {

    @Getter @Setter
    private boolean enabled;
    private static final Identifier BASE = Identifier.tryBuild(BazaarUtils.MODID, "widget/generic_widget_base");
    private static final Identifier HOVER = Identifier.tryBuild(BazaarUtils.MODID, "widget/generic_widget_hover");
    public static final WidgetSprites SLOT_BUTTON_TEXTURES = new WidgetSprites(
            BASE,
            HOVER);

    public BazaarOpenOrdersButton(boolean enabled) {
        this.enabled = enabled;
    }

    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidget() {
        if(!BUConfig.get().bazaarOpenOrdersButton.isEnabled())
            return Collections.emptyList();

        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        if (!(VersionCompat.getScreen(Minecraft.getInstance()) instanceof AccessorAbstractContainerScreen screen) || !screenInfo.inBazaar())
            return Collections.emptyList();


        ItemSlotButtonWidget.ScreenWidgetDimensions dimensions = ItemSlotButtonWidget.getSafeScreenDimensions(screen, screenInfo.getContainerName());

        ItemSlotButtonWidget button = getItemSlotButtonWidget(dimensions);
        return Collections.singletonList(button);
    }

    private static ItemSlotButtonWidget getItemSlotButtonWidget(ItemSlotButtonWidget.ScreenWidgetDimensions dimensions) {
        int buttonSize = 18;
        int spacing = 4;
        int buttonOffset = 18; // to avoid overlap with other buttons since this is the second button down
        int buttonX = dimensions.x() - buttonSize - spacing;
        int currentButtonY = dimensions.y() + spacing + (buttonOffset + spacing) * 1;

        return new ItemSlotButtonWidget(
                buttonX,
                currentButtonY,
                buttonSize, buttonSize,
                SLOT_BUTTON_TEXTURES,
                (btn) -> {
//                    GUIUtils.closeHandledScreen();
                    PlayerActionUtil.runCommand("managebazaarorders");
                },
                Items.BOOK.getDefaultInstance(),
                Component.literal("Go to Orders (Requires Cookie)")
        );
    }
}
