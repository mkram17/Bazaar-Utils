package com.github.mkram17.bazaarutils.features;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.misc.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.misc.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.misc.widgets.TextDisplayWidget;
import com.github.mkram17.bazaarutils.mixin.AccessorAbstractContainerScreen;

import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.VersionCompat;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class OrderLimit implements BUListener {
    @Getter @Setter
    private boolean enabled;
    @Getter @Setter
    private double coinLimit;

    @Getter
    private final List<OrderLimitEntry> orderLimitEntries;

    private double getTotalOrderedCoins() {
        return orderLimitEntries.stream().mapToDouble(OrderLimitEntry::price).sum();
    }

    public OrderLimit(boolean enabled, double coinLimit) {
        this.enabled = enabled;
        this.coinLimit = coinLimit;
        this.orderLimitEntries = new ArrayList<>();
    }


    @RunOnInit
    public static void registerBazaarOpen() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
            if(screenInfo == null || !screenInfo.inBazaar())
                return;
            BUConfig.get().orderLimit.removeOldEntries();
        });
    }

    public void removeOldEntries() {
        orderLimitEntries.stream().filter((entry) -> entry.time().isBefore(TimeUtil.LAST_BAZAAR_LIMIT_RESET_TIME)).toList()
                .forEach(orderLimitEntries::remove);
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

    public void addOrderToLimit(double price) {
        orderLimitEntries.add(new OrderLimitEntry(price, ZonedDateTime.now()));
    }

    @RegisterWidget
    public static List<AbstractWidget> getWidget() {
        OrderLimit orderLimit = BUConfig.get().orderLimit;
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        boolean isTargetScreen = screenInfo.inBazaar();
        if (!orderLimit.isEnabled() || !isTargetScreen || !(VersionCompat.getScreen(Minecraft.getInstance()) instanceof AccessorAbstractContainerScreen screen))
            return Collections.emptyList();

        String screenTitle = VersionCompat.getScreen(Minecraft.getInstance()).getTitle().getString();
        String orderedCoinsFormatted = formatNumberWithPrefix(orderLimit.getTotalOrderedCoins());

        Component orderedCoinsText = orderLimit.getTotalOrderedCoins() >= orderLimit.getCoinLimit() ? Component.literal(orderedCoinsFormatted).withStyle(ChatFormatting.RED) : Component.literal(orderedCoinsFormatted).withStyle(ChatFormatting.GREEN);
        Component limitText = Component.literal("/" + formatNumberWithPrefix(orderLimit.getCoinLimit())).withStyle(ChatFormatting.GOLD);
        Component message = Component.literal("Bazaar Order Limit: ").withStyle(ChatFormatting.GOLD)
                .append(orderedCoinsText)
                .append(limitText);

        ItemSlotButtonWidget.ScreenWidgetDimensions dimensions = ItemSlotButtonWidget.getSafeScreenDimensions(screen,
                screenTitle);

        int textSizeX = 58;
        int textSizeY = 8;
        int spacing = 5;
        if(BUCompatibilityHelper.isSkyblockerLoaded())
            spacing += 21;
        int buttonX = dimensions.x() + textSizeX;
        int buttonY = dimensions.y() - spacing - textSizeY;
        TextDisplayWidget widget = new TextDisplayWidget(buttonX, buttonY, textSizeX, textSizeY, message);
        return Collections.singletonList(widget);
    }

    @Override
    public void subscribe() {
        BazaarUtils.EVENT_BUS.subscribe(this);
    }

    public record OrderLimitEntry(@Getter double price, @Getter ZonedDateTime time) {
            public OrderLimitEntry(double price, ZonedDateTime time) {
                this.price = price;
                this.time = time;
                Util.scheduleConfigSave();
            }
        }

    public OptionGroup buildOrderLimitGroup() {
        OptionGroup.Builder restrictSellGroupBuilder = OptionGroup.createBuilder()
                .name(Component.literal("Order Limit"))
                .description(OptionDescription.of(Component.literal("Shows you how close you are to the coin order limit for the bazaar. Resets at 12am GMT.")));

        buildOptions(restrictSellGroupBuilder);

        return restrictSellGroupBuilder.build();
    }
    private void buildOptions(OptionGroup.Builder builder){
            builder.option(createEnabledOption());
            builder.option(createLimitOption());
    }


    private Option<Boolean> createEnabledOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Bazaar Order Limit"))
                .description(OptionDescription.of(Component.literal("Shows you how close you are to the coin order limit for the bazaar at the top of the bazaar. Resets at 12am GMT.")))
                .binding(false,
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfigGui::createBooleanController)
                .build();
    }

    private Option<Double> createLimitOption() {
        return Option.<Double>createBuilder()
                .name(Component.literal("Order Limit"))
                .description(OptionDescription.of(Component.literal("The order limit you want to display.")))
                .binding(coinLimit,
                        this::getCoinLimit,
                        this::setCoinLimit)
                .controller(DoubleFieldControllerBuilder::create)
                .build();
    }
}
