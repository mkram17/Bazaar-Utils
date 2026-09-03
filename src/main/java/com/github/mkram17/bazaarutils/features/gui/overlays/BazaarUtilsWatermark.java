package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.LogoDisplayWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;

/**
 * Marks Bazaar screens with the mod's logo and name in the top left corner.
 */
@Module
public class BazaarUtilsWatermark implements ToggleableFeature {
    public static final Identifier LOGO = Identifier.tryBuild(BazaarUtils.MOD_ID, "logo");

    private static final int MARGIN = 4;
    private static final int LOGO_SIZE = 16;

    @Override
    public boolean isEnabled() {
        return OverlaysConfig.WATERMARK_TOGGLE;
    }

    public BazaarUtilsWatermark() {}

    @RegisterWidget
    public static List<LogoDisplayWidget> getWidgets() {
        if (!BazaarUtilsModules.BazaarUtilsWatermark.isEnabled()) {
            return Collections.emptyList();
        }

        // Anchored to the screen corner rather than to the container, so unlike the other overlays
        // this only needs to know a Bazaar screen is open — not where its GUI sits.
        if (!ScreenManager.getInstance().isCurrent(BazaarScreenType.values())) {
            return Collections.emptyList();
        }

        return List.of(new LogoDisplayWidget(
                MARGIN,
                MARGIN,
                LOGO_SIZE,
                LOGO,
                Component.literal(BazaarUtils.MOD_NAME).withStyle(ChatFormatting.GOLD)
        ));
    }
}
