package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.features.AutoFlipper;
import com.github.mkram17.bazaarutils.features.CustomOrder;
import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;


public class BUConfig {
    public static ConfigClassHandler<BUConfig> HANDLER = ConfigClassHandler.createBuilder(BUConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("bazaarutils.json"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting) // not needed, pretty print by default
                    .setJson5(true)

                    .build())
            .build();

    public static CustomOrder maxBuyOrder = new CustomOrder(() -> BUConfig.buyMaxEnabled, () -> 71680, () -> 17, Items.PURPLE_STAINED_GLASS_PANE);

    @SerialEntry
    public static ArrayList<ItemData> watchedItems = new ArrayList<>();
    @SerialEntry
    public static double bzTax = 0.01125;
    @SerialEntry @Setter @Getter
    public static boolean autoFlip;
    @SerialEntry
    public static int outdatedTiming = 5;
    @SerialEntry
    public static boolean notifyOutdated = true;
    @SerialEntry
    public static boolean buyMaxEnabled = true;
    @SerialEntry
    public static boolean autoOpenBazaar = true;
    @SerialEntry
    public static ArrayList<CustomOrder> customOrders = new ArrayList<>(List.of(maxBuyOrder));

    public static void openGUI() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(BUConfig.createGUI(null)));
    }

    public static Screen createGUI(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> {
            builder.title(Text.literal("Bazaar Utils"));
            builder.category(ConfigCategory.createBuilder()
                    .name(Text.literal("General"))
                    .option(AutoFlipper.createOption())
                    .build()
            );
            // Create the OptionGroup builder
            OptionGroup.Builder customOrdersGroupBuilder = OptionGroup.createBuilder()
                    .name(Text.literal("Custom Orders"))
                    .description(OptionDescription.of(Text.literal("Add buttons for custom buy order amounts.")));

            // Add options to the OptionGroup builder
            for (CustomOrder order : customOrders) {
                customOrdersGroupBuilder.option(order.createOption());
            }
            builder.category(CustomOrder.createOrdersCategory().group(customOrdersGroupBuilder.build()).build());
            // Build the OptionGroup and add it to the category

            builder.category(
                    Developer.create()
                            .build());
            return builder;
        }).generateScreen(parent);
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> opt) {
        return BooleanControllerBuilder.create(opt).onOffFormatter().coloured(true);
    }

    public static class Developer {
        public static boolean allMessages = false;
        public static boolean errorMessages = false;
        public static boolean guiMessages = false;
        public static boolean featureMessages = false;
        public static boolean bazaarDataMessages = false;
        public static boolean commandMessages = false;
        public static boolean itemDataMessages = false;

        public static ConfigCategory.Builder create() {
            return ConfigCategory.createBuilder()
                    .name(Text.literal("Developer"))

                    .option(Option.<Boolean>createBuilder()
                            .name(Text.literal("All Messages"))
                            .binding(allMessages,
                                    () -> allMessages,
                                    newVal -> allMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build())

                    .option(Option.<Boolean>createBuilder()
                            .name(Text.literal("Error Messages"))
                            .binding(errorMessages,
                                    () -> errorMessages,
                                    newVal -> errorMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build())
                    .option(Option.<Boolean>createBuilder()
                            .name(Text.literal("GUI Messages"))
                            .binding(guiMessages,
                                    () -> guiMessages,
                                    newVal -> guiMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build())
                    .option(Option.<Boolean>createBuilder()
                            .name(Text.literal("Feature Messages"))
                            .binding(featureMessages,
                                    () -> featureMessages,
                                    newVal -> featureMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build())
                    .option(Option.<Boolean>createBuilder()
                            .name(Text.literal("Bazaar Data Messages"))
                            .binding(bazaarDataMessages,
                                    () -> bazaarDataMessages,
                                    newVal -> bazaarDataMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build())
                    .option(Option.<Boolean>createBuilder()
                            .name(Text.literal("Command Messages"))
                            .binding(commandMessages,
                                    () -> commandMessages,
                                    newVal -> commandMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build())
                    .option(Option.<Boolean>createBuilder()
                            .name(Text.literal("Item Data Messages"))
                            .binding(itemDataMessages,
                                    () -> itemDataMessages,
                                    newVal -> itemDataMessages = newVal)
                            .controller(BUConfig::createBooleanController)
                            .build());
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
