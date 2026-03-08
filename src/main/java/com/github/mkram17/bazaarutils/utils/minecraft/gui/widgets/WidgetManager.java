package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.ScreenChangeEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.mixin.AccessorHandledScreen;
import com.github.mkram17.bazaarutils.mixin.AccessorScreen;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;
import java.util.Optional;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public class WidgetManager {
    public record ScreenWidgetDimensions(int x, int y, int backgroundWidth) {}

    @RunOnInit
    public static void initialize() {
        EVENT_BUS.subscribe(WidgetManager.class);
    }

    @EventHandler
    private static void onScreenChange(ScreenChangeEvent event) {
        if (event.getOldScreen() != null) removeWidgetsFrom(event.getOldScreen());

        // causes a flash when onChestLoaded removes and re-adds immediately after.
        Screen next = event.getNewScreen();
        if (next == null || next instanceof GenericContainerScreen) return;

        addWidgetsTo(next);
    }

    @EventHandler(priority = EventPriority.LOW)
    private static void onChestLoaded(ChestLoadedEvent event) {
        Screen screen = event.getGenericContainerScreen();
        removeWidgetsFrom(screen);
        addWidgetsTo(screen);
    }

    private static void addWidgetsTo(Screen screen) {
        if (!(screen instanceof AccessorScreen accessor)) return;

        List<ClickableWidget> widgets = ConfigUtil.getWidgets();
        if (widgets.isEmpty()) return;

        widgets.forEach(accessor::registerWidget);
    }

    private static void removeWidgetsFrom(Screen screen) {
        if (!(screen instanceof AccessorScreen accessor)) return;

        accessor.getChildren().stream()
                .filter(element -> element instanceof ItemSlotButtonWidget || element instanceof TextDisplayWidget)
                .toList()
                .forEach(accessor::unregisterWidget);
    }

    public static Optional<ScreenWidgetDimensions> getScreenDimensions(ScreenType... required) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof AccessorHandledScreen screen)) {
            return Optional.empty();
        }

        if (!ScreenManager.getInstance().isCurrent(required)) {
            return Optional.empty();
        }

        int x = screen.getX();
        int y = screen.getY();
        int backgroundWidth = screen.getBackgroundWidth();

        if (backgroundWidth <= 0) {
            PlayerActionUtil.notifyAll("BackgroundWidth not yet initialized for " + ContainerManager.getContainerName(), NotificationType.GUI);
        }

        return Optional.of(new ScreenWidgetDimensions(x, y, backgroundWidth));
    }
}