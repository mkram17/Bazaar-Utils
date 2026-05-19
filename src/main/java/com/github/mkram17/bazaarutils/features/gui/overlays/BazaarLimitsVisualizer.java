package com.github.mkram17.bazaarutils.features.gui.overlays;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.data.bazaar.activity.BazaarActivityFold;
import com.github.mkram17.bazaarutils.data.bazaar.activity.BazaarActivityRecord;
import com.github.mkram17.bazaarutils.data.stored.BazaarActivityStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.TextDisplayWidget;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Module
public class BazaarLimitsVisualizer extends BUListener implements ToggleableFeature {
    private static final double COIN_LIMIT = 15_000_000_000d;

    @Override
    public boolean isEnabled() {
        return OverlaysConfig.BAZAAR_LIMITS_VISUALIZER_TOGGLE;
    }

    public BazaarLimitsVisualizer() {}

    private static final BazaarActivityFold<Double> ORDER_VALUE = BazaarActivityFold.filtering(
            record -> record instanceof BazaarActivityRecord.BuyOrderActivity
                    || record instanceof BazaarActivityRecord.SellOfferActivity
                    || record instanceof BazaarActivityRecord.FlipSellActivity,
            BazaarActivityFold.summingDouble(record -> switch (record) {
                case BazaarActivityRecord.BuyOrderActivity buy   -> buy.pricePerItem() * buy.originalAmount();
                case BazaarActivityRecord.SellOfferActivity sell -> sell.pricePerItem() * sell.originalAmount();
                case BazaarActivityRecord.FlipSellActivity flip  -> flip.pricePerItem() * flip.originalAmount();
                default -> 0d;
            }));

    private static final BazaarActivityFold<Double> INSTANT_VALUE = BazaarActivityFold.filtering(
            record -> record instanceof BazaarActivityRecord.InstantBuy
                    || record instanceof BazaarActivityRecord.InstantSell,
            BazaarActivityFold.summingDouble(record -> switch (record) {
                case BazaarActivityRecord.InstantBuy buy   -> buy.pricePerUnit() * buy.volume();
                case BazaarActivityRecord.InstantSell sell -> sell.pricePerUnit() * sell.volume();
                default -> 0d;
            }));

    private static final BazaarActivityFold<Double> TOTAL_LIMIT_VALUE = BazaarActivityFold.teeing(ORDER_VALUE, INSTANT_VALUE, Double::sum);

    private static double getTotalOrderedCoins() {
        return BazaarActivityStorage.foldToday(TOTAL_LIMIT_VALUE);
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