package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.features.OutdatedItems;
import com.github.mkram17.bazaarutils.features.StashHelper;
import com.github.mkram17.bazaarutils.features.autoflipper.AutoFlipper;
import com.github.mkram17.bazaarutils.features.autoflipper.AutoFlipperSettings;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrder;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrderSettings;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSellControl;
import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class BUConfig {
    public static final ConfigClassHandler<BUConfig> HANDLER = ConfigClassHandler.createBuilder(BUConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("bazaarutils.json"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting) // not needed, pretty print by default
                    .build())
            .build();

    public static BUConfig get() {
        return HANDLER.instance();
    }


    @SerialEntry
    public AutoFlipper autoFlipper = new AutoFlipper(new AutoFlipperSettings(true, 17, Items.CHERRY_SIGN));
    @SerialEntry
    public ArrayList<ItemData> watchedItems = new ArrayList<>();
    @SerialEntry
    public double bzTax = 0.01125;
    @SerialEntry
    public ArrayList<CustomOrder> customOrders = new ArrayList<>(List.of(
            new CustomOrder(new CustomOrderSettings(true, 71680, 17, CustomOrder.COLORMAP.get(0)))
    ));
    @SerialEntry
    public boolean developerMode = false;
    @SerialEntry
    public StashHelper stashHelper = new StashHelper(true);
    @SerialEntry
    public OutdatedItems outdatedItems = new OutdatedItems();
    //TODO make restrict sell able to take empty array list (might need to think about config gui group + options)
    @SerialEntry
    public RestrictSell restrictSell = new RestrictSell(true, 3, new ArrayList<>(List.of(new RestrictSellControl(RestrictSell.restrictBy.PRICE, 10000.0))));
    @SerialEntry
    public Developer developer = new Developer();

    public static void openGUI() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(BUConfig.get().createGUI(null)));
    }

    public Screen createGUI(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> {
            builder.title(Text.literal("Bazaar Utils"));
            OptionGroup.Builder restrictSellGroupBuilder = OptionGroup.createBuilder()
                    .name(Text.literal("Sell rules"))
                    .description(OptionDescription.of(Text.literal("Blocks insta selling based on rules. You can add a new rule with /bu rule add {based on volume or price} {amount over which will be restricted} or you can remove it with /bu rule remove {rule number}")));
            restrictSell.buildOptions(restrictSellGroupBuilder);
            if(restrictSell.getControls().isEmpty()) {
                builder.category(ConfigCategory.createBuilder()
                        .name(Text.literal("General"))
                        .option(autoFlipper.createOption())
                        .options(outdatedItems.createOptions())
                        .option(stashHelper.createOption())
                        .build()
                );
            } else {
                builder.category(ConfigCategory.createBuilder()
                        .name(Text.literal("General"))
                        .option(autoFlipper.createOption())
                        .options(outdatedItems.createOptions())
                        .option(stashHelper.createOption())
                        .group(restrictSellGroupBuilder.build())
                        .build()
                );
            }


            OptionGroup.Builder customOrdersGroupBuilder = OptionGroup.createBuilder()
                    .name(Text.literal("Custom Buy Amounts"))
                    .description(OptionDescription.of(Text.literal("Add buttons for custom buy order/insta buy amounts. To add more do /bu customorder add {order amount} {slot number} (top left slot is slot #1, to the right is #2, etc etc.")));

            CustomOrder.buildOptions(customOrdersGroupBuilder);
            builder.category(CustomOrder.createOrdersCategory().group(customOrdersGroupBuilder.build()).build());

            if(developerMode) {
                builder.category(
                        Developer.createDevBuilder()
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("All Messages"))
                                        .binding(developer.allMessages,
                                                () -> developer.allMessages,
                                                newVal -> developer.allMessages = newVal)
                                        .controller(BUConfig::createBooleanController)
                                        .build())

                                .group(
                                        OptionGroup.createBuilder()
                                                .name(Text.literal("Message Options"))
                                                .description(OptionDescription.of(Text.literal("DEVELOPER ONLY")))
                                                .options(developer.createOptions())
                                                .build()
                                )
                                .build());
            }
            return builder;
        }).generateScreen(parent);
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> opt) {
        return BooleanControllerBuilder.create(opt).onOffFormatter().coloured(true);
    }


    public static class Developer {
        public boolean allMessages = false;
        public boolean errorMessages = false;
        public boolean guiMessages = false;
        public boolean featureMessages = false;
        public boolean bazaarDataMessages = false;
        public boolean commandMessages = false;
        public boolean itemDataMessages = false;
        public static  ConfigCategory.Builder createDevBuilder(){
            return ConfigCategory.createBuilder()
                    .name(Text.literal("Developer"));
        }


        public Collection<? extends Option<?>> createOptions() {
            ArrayList<Option<?>> optionList = new ArrayList<>();
                    optionList.add(Option.<Boolean>createBuilder()
                            .name(Text.literal("Error Messages"))
                            .binding(errorMessages,
                                    () -> errorMessages,
                                    newVal -> errorMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build());
            optionList.add(Option.<Boolean>createBuilder()
                            .name(Text.literal("GUI Messages"))
                            .binding(guiMessages,
                                    () -> guiMessages,
                                    newVal -> guiMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build());
            optionList.add(Option.<Boolean>createBuilder()
                            .name(Text.literal("Feature Messages"))
                            .binding(featureMessages,
                                    () -> featureMessages,
                                    newVal -> featureMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build());
            optionList.add(Option.<Boolean>createBuilder()
                            .name(Text.literal("Bazaar Data Messages"))
                            .binding(bazaarDataMessages,
                                    () -> bazaarDataMessages,
                                    newVal -> bazaarDataMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build());
                    optionList.add(Option.<Boolean>createBuilder()
                            .name(Text.literal("Command Messages"))
                            .binding(commandMessages,
                                    () -> commandMessages,
                                    newVal -> commandMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build());
                    optionList.add(Option.<Boolean>createBuilder()
                            .name(Text.literal("Item Data Messages"))
                            .binding(itemDataMessages,
                                    () -> itemDataMessages,
                                    newVal -> itemDataMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build());
                    return optionList;
        }

        public boolean isDeveloperVariableEnabled(Util.notificationTypes type) {
            return switch (type) {
                case ERROR -> errorMessages;
                case GUI -> guiMessages;
                case FEATURE -> featureMessages;
                case BAZAARDATA -> bazaarDataMessages;
                case COMMAND -> commandMessages;
                case ITEMDATA -> itemDataMessages;
            };
        }
    }
}
