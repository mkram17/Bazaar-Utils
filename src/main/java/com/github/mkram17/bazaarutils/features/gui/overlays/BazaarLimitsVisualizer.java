package com.github.mkram17.bazaarutils.features.gui.overlays;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.data.BazaarLimitsStorage;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.util.BUToggleableFeature;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.mixin.AccessorHandledScreen;

import com.github.mkram17.bazaarutils.ui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.ui.widgets.TextDisplayWidget;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    public static boolean isEnabled() {
        return OverlaysConfig.BAZAAR_LIMITS_VISUALIZER_TOGGLE;
    }

    public BazaarLimitsVisualizer() {
    }

    @RunOnInit
    public static void registerBazaarOpen() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();

            if (!screenInfo.inBazaar()) {
                return;
            }

            BazaarLimitsVisualizer.removeOldEntries();
        });
    }

    public static String formatNumberWithPrefix(double number) {
        String prefix;
        double value;

        if (number >= 1_000_000_000) {
            prefix = "B";
            value = number / 1_000_000_000.0;
        } else {
            prefix = "M";
            value = number / 1_000_000.0;
        }

        return String.format("%.2f", value) + prefix;
    }

    public static void addOrderToLimit(double price) {
        if (price > Integer.MAX_VALUE){
            price = Integer.MAX_VALUE; // hypixel doesnt count coins over the integer limit
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
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();

        if (!(MinecraftClient.getInstance().currentScreen instanceof AccessorHandledScreen screen) || !screenInfo.inMenu(ScreenInfo.BazaarMenuType.BAZAAR_MAIN_PAGE)) {
            return Collections.emptyList();
        }

        String screenTitle = MinecraftClient.getInstance().currentScreen.getTitle().getString();
        ItemSlotButtonWidget.ScreenWidgetDimensions dimensions = ItemSlotButtonWidget.getSafeScreenDimensions(screen, screenTitle);

        return List.of(createLimitWidget(dimensions), createTimeUntilResetWidget(dimensions));
    }

    private static TextDisplayWidget createLimitWidget(ItemSlotButtonWidget.ScreenWidgetDimensions dimensions) {
        double ordered = BazaarLimitsVisualizer.getTotalOrderedCoins();
        String current = formatNumberWithPrefix(ordered);
        String max = formatNumberWithPrefix(BazaarLimitsVisualizer.COIN_LIMIT);

        Formatting color = (ordered >= BazaarLimitsVisualizer.COIN_LIMIT) ? Formatting.RED : Formatting.GREEN;
        Text message = Text.literal("Bazaar Order Limit: ").formatted(Formatting.GOLD)
                .append(Text.literal(current).formatted(color))
                .append(Text.literal(" / " + max).formatted(Formatting.GRAY));

        int spacing = BUCompatibilityHelper.isSkyblockerLoaded() ? 26 : 5;

        int x = dimensions.x();
        int y = dimensions.y() - spacing - OVERLAY_HEIGHT;

        return new TextDisplayWidget(x, y, OVERLAY_WIDTH, TEXT_HEIGHT, message, TextDisplayWidget.Alignment.LEFT);
    }

    private static TextDisplayWidget createTimeUntilResetWidget(ItemSlotButtonWidget.ScreenWidgetDimensions dimensions) {
        ZonedDateTime nextReset = TimeUtil.getNextBazaarLimitReset();
        Duration duration = Duration.between(ZonedDateTime.now(), nextReset);

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();

        Formatting urgencyColor = (hours < 1) ? Formatting.RED : (hours < 10 ? Formatting.YELLOW : Formatting.GRAY);

        String timeLabel = String.format("%dh %dm", hours, minutes);
        Text timeText = Text.literal("Until Reset: ").formatted(Formatting.GOLD)
                .append(Text.literal(timeLabel).formatted(urgencyColor));

        int spacing = BUCompatibilityHelper.isSkyblockerLoaded() ? 26 : 5; 

        int x = dimensions.x();
        int y = dimensions.y() - spacing - OVERLAY_HEIGHT + TEXT_HEIGHT + LINE_GAP;

        return new TextDisplayWidget(x, y, OVERLAY_WIDTH, TEXT_HEIGHT, timeText, TextDisplayWidget.Alignment.LEFT);
    }
}
