package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.handlers.ChatHandler;
import com.github.mkram17.bazaarutils.features.CustomOrder;
import com.github.mkram17.bazaarutils.features.FlipHelper;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URISyntaxException;

public class BUConfigGui {
    public static Screen create(Screen parent, BUConfig config) {
        return YetAnotherConfigLib.create(BUConfig.HANDLER, (defaults, cfg, builder) -> {
            builder.title(Component.literal(BazaarUtils.MOD_NAME));

            buildGeneralCategory(builder, config);
            buildCustomHelpersCategory(builder);

            if (config.developerMode) {
                buildDeveloperCategory(builder, config.developer);
            }

            return builder;
        }).generateScreen(parent);
    }

    private static void buildGeneralCategory(YetAnotherConfigLib.Builder builder, BUConfig config) {
        ConfigCategory.Builder generalBuilder = ConfigCategory.createBuilder()
                .name(Component.literal("General"));

        generalBuilder.option(config.flipHelper.createOption());
        generalBuilder.options(config.outbidOrderHandler.createOptions());
        generalBuilder.option(ChatHandler.createOrderFilledSoundOption());
        generalBuilder.option(config.stashMessages.createOption());
        generalBuilder.option(config.priceCharts.createOption());
        generalBuilder.option(config.orderStatusHighlight.createOption());
        generalBuilder.option(createDisableErrorNotifsOption(config));

        generalBuilder.group(buildRestrictSellGroup(config.restrictSell));
        generalBuilder.group(config.orderLimit.buildOrderLimitGroup());

        builder.category(generalBuilder.build());
    }

    private static OptionGroup buildRestrictSellGroup(RestrictSell restrictSell) {
        OptionGroup.Builder restrictSellGroupBuilder = OptionGroup.createBuilder()
                .name(Component.literal("Sell rules"))
                .description(OptionDescription.of(Component.literal("Blocks insta selling based on rules. You can add a new rule with /bu rule add {based on volume or price} {amount over which will be restricted} or you can remove it with /bu rule remove {rule number}")));

        if (restrictSell.getControls().isEmpty()) {
            restrictSell.addRule(RestrictSell.restrictBy.PRICE, 1000000);
        }
        restrictSell.buildOptions(restrictSellGroupBuilder);

        return restrictSellGroupBuilder.build();
    }

    private static void buildCustomHelpersCategory(YetAnotherConfigLib.Builder builder) {
      ConfigCategory.Builder customHelpersBuilder = ConfigCategory.createBuilder()
                .name(Component.literal("Custom Helpers"))
                .tooltip(Component.literal("Add or manage the functionality among the enabled helpers."));

      OptionGroup.Builder CustomOrderGroup = CustomOrder.createOrdersGroup();
      OptionGroup.Builder FlipHelperGroup = FlipHelper.createFlipsGroup();

      CustomOrder.buildOptions(CustomOrderGroup);
      FlipHelper.buildOptions(FlipHelperGroup);

      customHelpersBuilder.group(CustomOrderGroup.build());
      customHelpersBuilder.group(FlipHelperGroup.build());
  
      builder.category(customHelpersBuilder.build());
    }

    private static void buildDeveloperCategory(YetAnotherConfigLib.Builder builder, BUConfig.Developer developer) {
        ConfigCategory.Builder developerBuilder = ConfigCategory.createBuilder()
                .name(Component.literal("Developer"));

        developerBuilder.option(Option.<Boolean>createBuilder()
                .name(Component.literal("All Messages"))
                .binding(developer.allMessages,
                        () -> developer.allMessages,
                        newVal -> developer.allMessages = newVal)
                .controller(BUConfigGui::createBooleanController)
                .build());

        developerBuilder.group(
                OptionGroup.createBuilder()
                        .name(Component.literal("Message Options"))
                        .description(OptionDescription.of(Component.literal("DEVELOPER ONLY")))
                        .options(developer.createOptions())
                        .build());

        builder.category(developerBuilder.build());
    }

    private static Option<Boolean> createDisableErrorNotifsOption(BUConfig config) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Disable Error Notifications"))
                .description(OptionDescription.of(Component.literal("Not recommended to enable this unless you are experiencing error spam. This will disable all error notifications, but not the errors themselves.")))
                .binding(
                        config.disableErrorNotifications,
                        () -> config.disableErrorNotifications,
                        newVal -> config.disableErrorNotifications = newVal
                )
                .controller(BUConfigGui::createBooleanController)
                .build();
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> opt) {
        return BooleanControllerBuilder.create(opt).onOffFormatter().coloured(true);
    }

    public static <T extends Enum<T>> EnumControllerBuilder<T> createEnumController(Option<T> opt, Class<T> enumClass) {
        return EnumControllerBuilder.create(opt).enumClass(enumClass);
    }
}

