package com.github.mkram17.bazaarutils.features.gui.overlays;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.utils.storage.BazaarLimitsStorage;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.TextDisplayWidget;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Module
public class BazaarLimitsVisualizer extends BUListener implements BUToggleableFeature {
    private static final double COIN_LIMIT = 15_000_000_000d;

    public record OrderLimitEntry(double price, ZonedDateTime time) {}

    public static void saveLimits() {
        BazaarLimitsStorage.INSTANCE.save();
    }

    public static List<OrderLimitEntry> limits() {
        return BazaarLimitsStorage.INSTANCE.get();
    }

    @Override
    public boolean isEnabled() {
        return OverlaysConfig.BAZAAR_LIMITS_VISUALIZER_TOGGLE;
    }

    public BazaarLimitsVisualizer() {}

    @RunOnInit
    public static void registerBazaarOpen() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!ScreenManager.getInstance().isCurrent(BazaarScreens.ALL.toArray(ScreenType[]::new))) {
                return;
            }

            BazaarLimitsVisualizer.removeOldEntries();
        });
    }

    public static void addOrderToLimit(double price) {
        if (price > Integer.MAX_VALUE) {
            price = Integer.MAX_VALUE;
        }

        limits().add(new OrderLimitEntry(price, ZonedDateTime.now()));

        saveLimits();
    }

    public static void removeOldEntries() {
        limits()
                .stream()
                .filter((entry) -> entry.time().isBefore(TimeUtil.LAST_BAZAAR_LIMIT_RESET_TIME))
                .toList()
                .forEach(limits()::remove);

        saveLimits();
    }

    private static double getTotalOrderedCoins() {
        return limits()
                .stream()
                .mapToDouble(OrderLimitEntry::price)
                .sum();
    }

    private static final int TEXT_HEIGHT = 8;
    private static final int LINE_GAP = 4;
    private static final int OVERLAY_WIDTH = 116;
    private static final int OVERLAY_HEIGHT = TEXT_HEIGHT * 2 + LINE_GAP;

    @RegisterWidget
    public static List<TextDisplayWidget> getWidget() {
        if (!BazaarUtilsModules.BazaarLimitsVisualizer.isEnabled()) {
            return Collections.emptyList();
        }

        var dimensions = WidgetManager.getScreenDimensions(BazaarScreens.MAIN_PAGE);
        if (dimensions.isEmpty()) return Collections.emptyList();

        return List.of(createLimitWidget(dimensions.get()), createTimeUntilResetWidget(dimensions.get()));
    }

    private static TextDisplayWidget createLimitWidget(WidgetManager.ScreenWidgetDimensions dimensions) {
        double ordered = BazaarLimitsVisualizer.getTotalOrderedCoins();
        String current = Util.formatNumberWithPrefix(ordered);
        String max = Util.formatNumberWithPrefix(BazaarLimitsVisualizer.COIN_LIMIT);

        ChatFormatting color = (ordered >= BazaarLimitsVisualizer.COIN_LIMIT) ? ChatFormatting.RED : ChatFormatting.GREEN;
        Component message = Component.literal("Bazaar Order Limit: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(current).withStyle(color))
                .append(Component.literal(" / " + max).withStyle(ChatFormatting.GRAY));

        int spacing = BUCompatibilityHelper.isSkyblockerLoaded() ? 26 : 5;

        int x = dimensions.x();
        int y = dimensions.y() - spacing - OVERLAY_HEIGHT;

        return new TextDisplayWidget(x, y, OVERLAY_WIDTH, TEXT_HEIGHT, message, TextDisplayWidget.Alignment.LEFT);
    }

    private static TextDisplayWidget createTimeUntilResetWidget(WidgetManager.ScreenWidgetDimensions dimensions) {
        ZonedDateTime nextReset = TimeUtil.getNextBazaarLimitReset();
        Duration duration = Duration.between(ZonedDateTime.now(), nextReset);

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();

        ChatFormatting urgencyColor = (hours < 1) ? ChatFormatting.RED : (hours < 10 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);

        String timeLabel = String.format("%dh %dm", hours, minutes);
        Component timeText = Component.literal("Until Reset: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(timeLabel).withStyle(urgencyColor));

        int spacing = BUCompatibilityHelper.isSkyblockerLoaded() ? 26 : 5;

        int x = dimensions.x();
        int y = dimensions.y() - spacing - OVERLAY_HEIGHT + TEXT_HEIGHT + LINE_GAP;

        return new TextDisplayWidget(x, y, OVERLAY_WIDTH, TEXT_HEIGHT, timeText, TextDisplayWidget.Alignment.LEFT);
    }
}