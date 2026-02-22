package com.github.mkram17.bazaarutils.features.gui.buttons;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.mixin.AccessorHandledScreen;
import com.github.mkram17.bazaarutils.ui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

@Module
public class ModButtons {
    private static final Identifier DEFAULT_ORDERS = Identifier.tryParse(BazaarUtils.MOD_ID, "widget/generic_widget_base");
    private static final Identifier HOVERED_ORDERS = Identifier.tryParse(BazaarUtils.MOD_ID, "widget/generic_widget_hover");

    public static final ButtonTextures SLOT_ORDERS_BUTTON_TEXTURES = new ButtonTextures(
            DEFAULT_ORDERS,
            HOVERED_ORDERS
    );

    private static final Identifier DEFAULT_SETTINGS = Identifier.tryParse(BazaarUtils.MOD_ID, "widget/settings_widget_base");
    private static final Identifier HOVERED_SETTINGS = Identifier.tryParse(BazaarUtils.MOD_ID, "widget/settings_widget_hover");

    public static final ButtonTextures SLOT_SETTINGS_BUTTON_TEXTURES = new ButtonTextures(
            DEFAULT_SETTINGS,
            HOVERED_SETTINGS
    );

    public static boolean isEnabled() {
        return ButtonsConfig.OPEN_ORDERS_BUTTON.enabled || ButtonsConfig.OPEN_SETTINGS_BUTTON.enabled;
    }

    public ModButtons() {};

    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidget() {
        List<ItemSlotButtonWidget> result = new ArrayList<>();

        if (!isEnabled()) {
            return result;
        }

        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();

        if (!(MinecraftClient.getInstance().currentScreen instanceof AccessorHandledScreen screen) || !screenInfo.inBazaar()) {
            return result;
        }

        String screenTitle = MinecraftClient.getInstance().currentScreen.getTitle().getString();

        ItemSlotButtonWidget.ScreenWidgetDimensions dimensions = ItemSlotButtonWidget.getSafeScreenDimensions(screen, screenTitle);

        if (ButtonsConfig.OPEN_SETTINGS_BUTTON.isEnabled()) {
            result.add(createModSettingsButtonWidget(dimensions));
        }

        if (ButtonsConfig.OPEN_ORDERS_BUTTON.isEnabled()) {
            result.add(createBazaarOrdersButtonWidget(dimensions));
        }

        return result;
    }

    private static ItemSlotButtonWidget createModSettingsButtonWidget(ItemSlotButtonWidget.ScreenWidgetDimensions dimensions) {
        ButtonsConfig.WidgetButton config = ButtonsConfig.OPEN_SETTINGS_BUTTON;

        int buttonX = dimensions.x() - config.size - config.spacing;
        int currentButtonY = dimensions.y() + config.spacing;

        return new ItemSlotButtonWidget(
                buttonX,
                currentButtonY,
                config.size, config.size,
                SLOT_SETTINGS_BUTTON_TEXTURES,
                (widget) -> ConfigUtil.openGUI(),
                null,
                Text.literal("Bazaar Utils Settings")
        );
    }

    private static ItemSlotButtonWidget createBazaarOrdersButtonWidget(ItemSlotButtonWidget.ScreenWidgetDimensions dimensions) {
        ButtonsConfig.WidgetButton config = ButtonsConfig.OPEN_ORDERS_BUTTON;

        int buttonX = dimensions.x() - config.size - config.spacing;
        int currentButtonY = dimensions.y() + config.spacing + ((ButtonsConfig.OPEN_SETTINGS_BUTTON.enabled ? ButtonsConfig.OPEN_SETTINGS_BUTTON.size : 0) + config.spacing);

        return new ItemSlotButtonWidget(
                buttonX,
                currentButtonY,
                config.size, config.size,
                SLOT_ORDERS_BUTTON_TEXTURES,
                (widget) -> PlayerActionUtil.runCommand("managebazaarorders"),
                Items.BOOK.getDefaultStack(),
                Text.literal("Go to Orders (Requires Cookie)")
        );
    }
}
