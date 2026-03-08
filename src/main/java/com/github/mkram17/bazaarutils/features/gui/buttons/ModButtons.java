package com.github.mkram17.bazaarutils.features.gui.buttons;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Module
public class ModButtons implements BUToggleableFeature {
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

    @Override
    public boolean isEnabled() {
        return ButtonsConfig.OPEN_ORDERS_BUTTON.enabled || ButtonsConfig.OPEN_SETTINGS_BUTTON.enabled;
    }

    public ModButtons() {}

    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidget() {
        if (!BazaarUtilsModules.ModButtons.isEnabled()) {
            return Collections.emptyList();
        }

        var dimensions = WidgetManager.getScreenDimensions(BazaarScreens.ALL.toArray(ScreenType[]::new));
        if (dimensions.isEmpty()) return Collections.emptyList();

        List<ItemSlotButtonWidget> result = new ArrayList<>();

        if (ButtonsConfig.OPEN_SETTINGS_BUTTON.isEnabled()) {
            result.add(createModSettingsButtonWidget(dimensions.get()));
        }

        if (ButtonsConfig.OPEN_ORDERS_BUTTON.isEnabled()) {
            result.add(createBazaarOrdersButtonWidget(dimensions.get()));
        }

        return result;
    }

    private static ItemSlotButtonWidget createModSettingsButtonWidget(WidgetManager.ScreenWidgetDimensions dimensions) {
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

    private static ItemSlotButtonWidget createBazaarOrdersButtonWidget(WidgetManager.ScreenWidgetDimensions dimensions) {
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