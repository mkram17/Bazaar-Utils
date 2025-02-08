package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.features.AutoOpen;
import com.github.mkram17.bazaarutils.features.StashHelper;
import com.github.mkram17.bazaarutils.features.autoflipper.AutoFlipper;
import com.github.mkram17.bazaarutils.features.autoflipper.AutoFlipperSettings;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrder;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrderSettings;
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

import static com.github.mkram17.bazaarutils.config.BUConfig.Developer.allMessages;
import static com.github.mkram17.bazaarutils.config.BUConfig.Developer.createOptions;


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
    public int outdatedTiming = 5;
    @SerialEntry
    public boolean notifyOutdated = true;
    @SerialEntry
    public boolean buyMaxEnabled = true;
    @SerialEntry
    public ArrayList<CustomOrder> customOrders = new ArrayList<>();
    @SerialEntry
    public boolean developerMode = false;
    @SerialEntry
    public StashHelper stashHelper = new StashHelper();
    @SerialEntry
    public AutoOpen autoOpen = new AutoOpen();
    
    public static CustomOrder maxBuyOrder = new CustomOrder(new CustomOrderSettings(true, 71680, 17, CustomOrder.COLORMAP.get(0)));

    public static void openGUI() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(BUConfig.get().createGUI(null)));
    }

    public Screen createGUI(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> {
            builder.title(Text.literal("Bazaar Utils"));
            builder.category(ConfigCategory.createBuilder()
                    .name(Text.literal("General"))
                            .option(autoFlipper.createOption())
                            .option(autoOpen.createOption())
                            .option(stashHelper.createOption())
                    .build()
            );
            // Create the OptionGroup builder
            OptionGroup.Builder customOrdersGroupBuilder = OptionGroup.createBuilder()
                    .name(Text.literal("Custom Orders"))
                    .description(OptionDescription.of(Text.literal("Add buttons for custom buy order amounts.")));

            // Add options to the OptionGroup builder
            for (CustomOrder order : customOrders) {
                order.initializeStateManager();
                customOrdersGroupBuilder.option(order.createOption());
            }
            if(customOrders.isEmpty()){
                customOrders.add(maxBuyOrder);
                maxBuyOrder.initializeStateManager();
                customOrdersGroupBuilder.option(maxBuyOrder.createOption());
            }
            builder.category(CustomOrder.createOrdersCategory().group(customOrdersGroupBuilder.build()).build());
            // Build the OptionGroup and add it to the category

            if(developerMode) {
                builder.category(
                        Developer.createBuilder()
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("All Messages"))
                                        .binding(allMessages,
                                                () -> allMessages,
                                                newVal -> allMessages = newVal)
                                        .controller(BUConfig::createBooleanController)
                                        .build())

                                .group(
                                        OptionGroup.createBuilder()
                                                .name(Text.literal("Message Options"))
                                                .description(OptionDescription.of(Text.literal("DEVELOPER ONLY")))
                                                .options(createOptions())
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

    //TODO just add this to BUConfig or maybe make another class for it (perhaps other sections of config could have their own classes as well, maybe make settings do this)
    public static class Developer {
        public static boolean allMessages = false;
        public static boolean errorMessages = false;
        public static boolean guiMessages = false;
        public static boolean featureMessages = false;
        public static boolean bazaarDataMessages = false;
        public static boolean commandMessages = false;
        public static boolean itemDataMessages = false;
        public static  ConfigCategory.Builder createBuilder(){
            return ConfigCategory.createBuilder()
                    .name(Text.literal("Developer"));
        }


        public static Collection<? extends Option<?>> createOptions() {
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

        public static boolean isDeveloperVariableEnabled(Util.notificationTypes type) {
            return switch (type) {
                case ERROR -> errorMessages;
                case GUI -> guiMessages;
                case FEATURE -> featureMessages;
                case BAZAARDATA -> bazaarDataMessages;
                case COMMAND -> commandMessages;
                case ITEMDATA -> itemDataMessages;
                default -> throw new IllegalArgumentException("Unknown type: " + type);
            };
        }

        public String getText(boolean example) {
            if (example) return "I'm in Example mode";
            if (!ItemData.nameList.isEmpty()) {
                ItemData.updateLists();
                return String.join(", ", ItemData.nameList);
            } else return "No watched items";
        }
    }
}
