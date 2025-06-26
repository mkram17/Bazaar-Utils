package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.ChatHandler;
import com.github.mkram17.bazaarutils.features.*;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSellControl;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.misc.CustomItemButton;
import com.github.mkram17.bazaarutils.misc.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.misc.ItemStackCodecGsonAdapter;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class BUConfig {
    public static RuntimeTypeAdapterFactory<CustomOrder> customOrderAdapterFactory = RuntimeTypeAdapterFactory.of(CustomOrder.class)
            .registerSubtype(MaxBuyOrder.class)
            .registerSubtype(CustomOrder.class);


    public static final ConfigClassHandler<BUConfig> HANDLER = ConfigClassHandler.createBuilder(BUConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("bazaarutils.json"))
                    .appendGsonBuilder(gsonBuilder -> gsonBuilder
                            .registerTypeAdapter(ItemStack.class, new ItemStackCodecGsonAdapter())
                            .registerTypeAdapterFactory(customOrderAdapterFactory))
                    .build())
            .build();

    public static BUConfig get() {
        return HANDLER.instance();
    }


    @SerialEntry
    public String MODVERSION = "";
    @SerialEntry
    public boolean firstLoad = true;
    @SerialEntry
    public FlipHelper flipHelper = new FlipHelper(true, 17, Items.CHERRY_SIGN);
    @SerialEntry
    public ArrayList<OrderData> watchedOrders = new ArrayList<>();
    @SerialEntry
    public double bzTax = 1.125;
    @SerialEntry
    public ArrayList<CustomOrder> customOrders = new ArrayList<>(List.of(new MaxBuyOrder(true)));
    @SerialEntry
    public boolean developerMode = false;
    @SerialEntry
    public OutdatedItems outdatedItems = new OutdatedItems(false, true);
    //TODO make restrict sell able to take empty array list (might need to think about config gui group + options)
    @SerialEntry
    public RestrictSell restrictSell = new RestrictSell(true, 3, new ArrayList<>(List.of(new RestrictSellControl(RestrictSell.restrictBy.PRICE, 1000000))));
    @SerialEntry
    public Developer developer = new Developer();
    @SerialEntry
    public StashMessages stashMessages = new StashMessages(false);
    @SerialEntry
    public ArrayList<Bookmark> bookmarks = new ArrayList<>();
    @SerialEntry
    public PriceCharts priceCharts = new PriceCharts();
    @SerialEntry
    public OrderStatusHighlight orderStatusHighlight = new OrderStatusHighlight(true);
    @SerialEntry @Getter @Setter
    public boolean disableErrorNotifications = false;
    @SerialEntry @Getter @Setter
    public boolean orderFilledSound = true;
    @SerialEntry
    public OrderPriceCapture orderPriceCapture = new OrderPriceCapture(true);


    public static void openGUI() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(BUConfig.get().createGUI(null)));
    }

    public Screen createGUI(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> {
            builder.title(Text.literal("Bazaar utils"));
            OptionGroup.Builder restrictSellGroupBuilder = OptionGroup.createBuilder()
                    .name(Text.literal("Sell rules"))
                    .description(OptionDescription.of(Text.literal("Blocks insta selling based on rules. You can add a new rule with /bu rule add {based on volume or price} {amount over which will be restricted} or you can remove it with /bu rule remove {rule number}")));
            if (restrictSell.getControls().isEmpty()) {
                restrictSell.addRule(RestrictSell.restrictBy.PRICE, 1000000);
            }
            restrictSell.buildOptions(restrictSellGroupBuilder);

            ConfigCategory.Builder generalBuilder = ConfigCategory.createBuilder();
            generalBuilder.name(Text.literal("General"))
                    .option(orderPriceCapture.createOption())
                    .option(flipHelper.createOption())
                    .options(outdatedItems.createOptions())
                    .option(ChatHandler.createDisableOrderFilledSound())
                    .option(stashMessages.createOption())
                    .option(priceCharts.createOption())
                    .option(orderStatusHighlight.createOption())
                    .option(orderStatusHighlight.createFilledHighlightOption())
                    .option(createDisableErrorNotifsOption());
            if(!BUCompatibilityHelper.isAmecsReborn())
                generalBuilder.option(createAmecsDownloadButton());

            generalBuilder.group(restrictSellGroupBuilder.build());

            builder.category(generalBuilder.build());

            OptionGroup.Builder customOrdersGroupBuilder = OptionGroup.createBuilder()
                    .name(Text.literal("Custom Buy Amounts"))
                    .description(OptionDescription.of(Text.literal("Add buttons for custom buy order/insta buy amounts. To add more do /bu customorder add {order amount} {slot number} (top left slot is slot #1, to the right is #2, etc etc.")));

            CustomOrder.buildOptions(customOrdersGroupBuilder);
            builder.category(CustomOrder.createOrdersCategory().group(customOrdersGroupBuilder.build()).build());


            ConfigCategory.Builder developerBuilder = Developer.createDevBuilder();
            
            if (developerMode) {
                developerBuilder.option(Option.<Boolean>createBuilder()
                        .name(Text.literal("All Messages"))
                        .binding(developer.allMessages,
                                () -> developer.allMessages,
                                newVal -> developer.allMessages = newVal)
                        .controller(BUConfig::createBooleanController)
                        .build())
                        .group(
                                OptionGroup.createBuilder()
                                        .name(Text.literal("Message Options"))
                                        .description(OptionDescription.of(Text.literal("Configure which developer messages to show")))
                                        .options(developer.createOptions())
                                        .build()
                        );
            } else {
                developerBuilder.option(
                        ButtonOption.createBuilder()
                                .name(Text.literal("Enable Developer Mode"))
                                .description(OptionDescription.of(Text.literal("Developer mode is currently disabled. Use '/bu developer' command to enable it and access developer settings.")))
                                .text(Text.literal("Run '/bu developer'"))
                                .action((yaclScreen, buttonOption) -> {
                                    // Close the config screen and run the command
                                    MinecraftClient.getInstance().setScreen(null);
                                    MinecraftClient.getInstance().player.networkHandler.sendChatCommand("bu developer");
                                })
                                .build()
                );
            }
            
            builder.category(developerBuilder.build());

            return builder;
        }).generateScreen(parent);
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> opt) {
        return BooleanControllerBuilder.create(opt).onOffFormatter().coloured(true);
    }

    public List<BUListener> getSerializedEvents() {
        List<BUListener> events = new ArrayList<>();

        for (Field field : this.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(this);

                if (value instanceof BUListener) {
                    events.add((BUListener) value);
                }
                else if (value instanceof Collection) {
                    for (Object item : (Collection<?>) value) {
                        if (item instanceof BUListener) {
                            events.add((BUListener) item);
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                Util.notifyError("Error accessing field: " + field.getName() + " - " + e.getMessage(), e);
            }
        }
        return events;


    }

    public static List<ItemSlotButtonWidget> getWidgets(){
        List<ItemSlotButtonWidget> widgets = new ArrayList<>();

        widgets.addAll(Bookmark.getWidgets());
        widgets.addAll(BazaarSettingsButton.getWidget());
        return widgets;
    }
    private static ButtonOption createAmecsDownloadButton() {
        return ButtonOption.createBuilder()
                .name(Text.of("Download Amecs Reborn"))
                .description(OptionDescription.of(Text.of("Amecs Reborn is needed for the Stash Helper feature. Download here.")))
                .text(Text.of("(for Stash Helper)")) // optional
                .action((yaclScreen, buttonOption) -> {
                    MinecraftClient.getInstance().setScreen(new ConfirmLinkScreen((confirmed) -> {
                        if (confirmed) {
                            try {
                                net.minecraft.util.Util.getOperatingSystem().open(new URI("https://modrinth.com/mod/amecs-reborn"));
                            } catch (URISyntaxException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        MinecraftClient.getInstance().setScreen(null);
                    }, "https://modrinth.com/mod/amecs-reborn", true));
                })
                .build();
    }

    private Option<Boolean> createDisableErrorNotifsOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Disable Error Notifications"))
                .description(OptionDescription.of(Text.literal("Not recommended to enable this unless you are experiencing error spam. This will disable all error notifications, but not the errors themselves.")))
                .binding(false,
                        this::isDisableErrorNotifications,
                        this::setDisableErrorNotifications)
                .controller(BUConfig::createBooleanController)
                .build();
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
                case GUI -> guiMessages;
                case FEATURE -> featureMessages;
                case BAZAARDATA -> bazaarDataMessages;
                case COMMAND -> commandMessages;
                case ITEMDATA -> itemDataMessages;
            };
        }
    }
}
