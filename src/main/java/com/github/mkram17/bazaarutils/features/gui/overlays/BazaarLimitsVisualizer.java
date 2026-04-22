package com.github.mkram17.bazaarutils.features.gui.overlays;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarChatEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.codecs.ZonedDateTimeCodec;
import com.github.mkram17.bazaarutils.data.BazaarLimitsStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.TextDisplayWidget;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

@Module
public class BazaarLimitsVisualizer extends BUListener implements ToggleableFeature {
    private static final BazaarLogger LOG = BazaarLogger.of(BazaarLimitsVisualizer.class);

    private static final double COIN_LIMIT = 15_000_000_000d;

    public record OrderLimitEntry(double price, ZonedDateTime time) {
        public static final Codec<OrderLimitEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("price").forGetter(OrderLimitEntry::price),
                ZonedDateTimeCodec.CODEC.fieldOf("time").forGetter(OrderLimitEntry::time)
        ).apply(instance, OrderLimitEntry::new));
    }

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

    @Subscription
    public void onOrderPlaced(UserOrderEvent.Placed event) {
        addOrderToLimit(event.getOrder().pricePerItem() * event.getOrder().originalAmount());
    }

    @Subscription
    public void onInstantBuy(BazaarChatEvent.InstantBuy event) {
        addOrderToLimit(event.getOrder().getPricePerItem() * event.getOrder().getVolume());
    }

    // > = less priority, albeit run this whenever
    @Subscription(priority = Integer.MAX_VALUE)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(all = true)
    public void onChestLoaded(ChestLoadedEvent event) {
        BazaarLimitsVisualizer.removeOldEntries();
    }

    @RunOnInit
    public static void registerBazaarOpen() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!ScreenManager.getInstance().isCurrent(BazaarScreenType.values())) {
                return;
            }

            BazaarLimitsVisualizer.removeOldEntries();
        });
    }

    public static void addOrderToLimit(double price) {
        if (price > Integer.MAX_VALUE) {
            LOG.warn("Order price {} exceeds Integer.MAX_VALUE — capping", price);

            price = Integer.MAX_VALUE;
        }

        final double finalPrice = price;

        BazaarLimitsStorage.INSTANCE.edit(list -> list.add(new OrderLimitEntry(finalPrice, ZonedDateTime.now())));
        PlayerLogger.debug("Limit entry added — price=%.0f total=%.0f / %.0f".formatted(finalPrice, getTotalOrderedCoins(), COIN_LIMIT), NotificationType.FEATURE);
    }

    public static void removeOldEntries() {
        BazaarLimitsStorage.INSTANCE.edit(list -> {
            int before = list.size();
            list.removeIf(entry -> entry.time().isBefore(TimeUtil.LAST_BAZAAR_LIMIT_RESET_TIME));
            int removed = before - list.size();
            if (removed > 0) LOG.debug("Removed {} stale limit entries — {} remaining", removed, list.size());
        });
    }

    private static double getTotalOrderedCoins() {
        var list = BazaarLimitsStorage.INSTANCE.get();
        if (list == null) return 0.0;

        return list.stream().mapToDouble(OrderLimitEntry::price).sum();
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

        var dimensions = WidgetManager.getScreenDimensions(BazaarScreenType.MAIN_PAGE);
        if (dimensions.isEmpty()) {
            LOG.info("BazaarLimitsVisualizer: no screen dimensions — widget skipped");

            return Collections.emptyList();
        }

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