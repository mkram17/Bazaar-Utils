package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.ChatHandler;
import com.github.mkram17.bazaarutils.features.*;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSellControl;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import com.github.mkram17.bazaarutils.misc.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.misc.ItemStackCodecGsonAdapter;
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

/**
 * Main configuration class for Bazaar Utils mod.
 * Handles all configuration options including features, developer settings, and GUI generation.
 */
public class BUConfig {
    // Configuration constants
    private static final double DEFAULT_BZ_TAX = 1.125;
    private static final int DEFAULT_RESTRICT_PRICE = 1000000;
    private static final int DEFAULT_FLIP_HELPER_SLOT = 17;
    private static final String AMECS_REBORN_URL = "https://modrinth.com/mod/amecs-reborn";
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
    public FlipHelper flipHelper = new FlipHelper(true, DEFAULT_FLIP_HELPER_SLOT, Items.CHERRY_SIGN);
    @SerialEntry
    public ArrayList<OrderData> watchedOrders = new ArrayList<>();
    @SerialEntry
    public double bzTax = DEFAULT_BZ_TAX;
    @SerialEntry
    public ArrayList<CustomOrder> customOrders = new ArrayList<>(List.of(new MaxBuyOrder(true)));
    @SerialEntry
    public boolean developerMode = false;
    @SerialEntry
    public OutdatedItems outdatedItems = new OutdatedItems(false, true);
    // TODO: make restrict sell able to take empty array list (might need to think about config gui group + options)
    @SerialEntry
    public RestrictSell restrictSell = new RestrictSell(true, 3, new ArrayList<>(List.of(new RestrictSellControl(RestrictSell.restrictBy.PRICE, DEFAULT_RESTRICT_PRICE))));
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


    /**
     * Opens the configuration GUI.
     */
    public static void openGUI() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(BUConfig.get().createGUI(null)));
    }

    /**
     * Creates the main configuration GUI screen.
     * @param parent The parent screen to return to when closing
     * @return The generated configuration screen
     */
    public Screen createGUI(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> {
            builder.title(Text.literal("Bazaar utils"));
            
            // Build all configuration categories
            builder.category(createGeneralCategory());
            builder.category(createCustomOrdersCategory());
            
            if (developerMode) {
                builder.category(createDeveloperCategory());
            }
            
            return builder;
        }).generateScreen(parent);
    }

    /**
     * Creates the general configuration category with core features.
     * @return ConfigCategory.Builder for the general category
     */
    private ConfigCategory.Builder createGeneralCategory() {
        OptionGroup.Builder restrictSellGroupBuilder = createRestrictSellGroup();
        
        ConfigCategory.Builder generalBuilder = ConfigCategory.createBuilder();
        generalBuilder.name(Text.literal("General"))
                .option(flipHelper.createOption())
                .options(outdatedItems.createOptions())
                .option(ChatHandler.createDisableOrderFilledSound())
                .option(stashMessages.createOption())
                .option(priceCharts.createOption())
                .option(orderStatusHighlight.createOption())
                .option(createDisableErrorNotifsOption());
        
        if (!BUCompatibilityHelper.isAmecsReborn()) {
            generalBuilder.option(createAmecsDownloadButton());
        }
        
        generalBuilder.group(restrictSellGroupBuilder.build());
        return generalBuilder;
    }

    /**
     * Creates the restrict sell option group.
     * @return OptionGroup.Builder for restrict sell rules
     */
    private OptionGroup.Builder createRestrictSellGroup() {
        OptionGroup.Builder restrictSellGroupBuilder = OptionGroup.createBuilder()
                .name(Text.literal("Sell rules"))
                .description(OptionDescription.of(Text.literal("Blocks insta selling based on rules. You can add a new rule with /bu rule add {based on volume or price} {amount over which will be restricted} or you can remove it with /bu rule remove {rule number}")));
        
        if (restrictSell.getControls().isEmpty()) {
            restrictSell.addRule(RestrictSell.restrictBy.PRICE, DEFAULT_RESTRICT_PRICE);
        }
        restrictSell.buildOptions(restrictSellGroupBuilder);
        
        return restrictSellGroupBuilder;
    }

    /**
     * Creates the custom orders configuration category.
     * @return ConfigCategory.Builder for custom orders
     */
    private ConfigCategory.Builder createCustomOrdersCategory() {
        OptionGroup.Builder customOrdersGroupBuilder = OptionGroup.createBuilder()
                .name(Text.literal("Custom Buy Amounts"))
                .description(OptionDescription.of(Text.literal("Add buttons for custom buy order/insta buy amounts. To add more do /bu customorder add {order amount} {slot number} (top left slot is slot #1, to the right is #2, etc etc.")));

        CustomOrder.buildOptions(customOrdersGroupBuilder);
        return CustomOrder.createOrdersCategory().group(customOrdersGroupBuilder.build());
    }

    /**
     * Creates the developer configuration category.
     * @return ConfigCategory.Builder for developer options
     */
    private ConfigCategory.Builder createDeveloperCategory() {
        return Developer.createDevBuilder()
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
                );
    }

    /**
     * Creates a boolean controller with on/off formatting and colors.
     * @param opt The boolean option to create a controller for
     * @return BooleanControllerBuilder with standard formatting
     */
    public static BooleanControllerBuilder createBooleanController(Option<Boolean> opt) {
        return BooleanControllerBuilder.create(opt).onOffFormatter().coloured(true);
    }

    /**
     * Gets all serialized event listeners from this configuration.
     * Uses reflection to find all BUListener instances in fields.
     * @return List of BUListener instances
     */
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

    /**
     * Gets all UI widgets from various features.
     * @return List of ItemSlotButtonWidget instances
     */
    public static List<ItemSlotButtonWidget> getWidgets(){
        List<ItemSlotButtonWidget> widgets = new ArrayList<>();

        widgets.addAll(Bookmark.getWidgets());
        widgets.addAll(BazaarSettingsButton.getWidget());
        return widgets;
    }
    /**
     * Creates a button to download Amecs Reborn mod.
     * @return ButtonOption for downloading Amecs Reborn
     */
    private static ButtonOption createAmecsDownloadButton() {
        return ButtonOption.createBuilder()
                .name(Text.of("Download Amecs Reborn"))
                .description(OptionDescription.of(Text.of("Amecs Reborn is needed for the Stash Helper feature. Download here.")))
                .text(Text.of("(for Stash Helper)"))
                .action((yaclScreen, buttonOption) -> {
                    MinecraftClient.getInstance().setScreen(new ConfirmLinkScreen((confirmed) -> {
                        if (confirmed) {
                            try {
                                net.minecraft.util.Util.getOperatingSystem().open(new URI(AMECS_REBORN_URL));
                            } catch (URISyntaxException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        MinecraftClient.getInstance().setScreen(null);
                    }, AMECS_REBORN_URL, true));
                })
                .build();
    }

    /**
     * Creates an option to disable error notifications.
     * @return Option for disabling error notifications
     */
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
    /**
     * Developer configuration section.
     * Contains settings for debug messages and developer-only features.
     */
    public static class Developer {
        public boolean allMessages = false;
        public boolean errorMessages = false;
        public boolean guiMessages = false;
        public boolean featureMessages = false;
        public boolean bazaarDataMessages = false;
        public boolean commandMessages = false;
        public boolean itemDataMessages = false;
        
        /**
         * Creates the developer category builder.
         * @return ConfigCategory.Builder for developer settings
         */
        public static ConfigCategory.Builder createDevBuilder(){
            return ConfigCategory.createBuilder()
                    .name(Text.literal("Developer"));
        }

        /**
         * Helper method to create a boolean option for developer message types.
         * Reduces code duplication and improves maintainability.
         */
        private Option<Boolean> createBooleanOption(String name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            return Option.<Boolean>createBuilder()
                    .name(Text.literal(name))
                    .binding(getter.get(), getter, setter)
                    .controller(BUConfig::createBooleanController)
                    .build();
        }

        /**
         * Creates configuration options for all developer message types.
         * @return Collection of boolean options for different message categories
         */
        public Collection<? extends Option<?>> createOptions() {
            ArrayList<Option<?>> optionList = new ArrayList<>();
            
            optionList.add(createBooleanOption("Error Messages", 
                    () -> errorMessages, 
                    newVal -> errorMessages = newVal));
            
            optionList.add(createBooleanOption("GUI Messages", 
                    () -> guiMessages, 
                    newVal -> guiMessages = newVal));
            
            optionList.add(createBooleanOption("Feature Messages", 
                    () -> featureMessages, 
                    newVal -> featureMessages = newVal));
            
            optionList.add(createBooleanOption("Bazaar Data Messages", 
                    () -> bazaarDataMessages, 
                    newVal -> bazaarDataMessages = newVal));
            
            optionList.add(createBooleanOption("Command Messages", 
                    () -> commandMessages, 
                    newVal -> commandMessages = newVal));
            
            optionList.add(createBooleanOption("Item Data Messages", 
                    () -> itemDataMessages, 
                    newVal -> itemDataMessages = newVal));
            
            return optionList;
        }

        /**
         * Checks if a specific developer message type is enabled.
         * @param type The notification type to check
         * @return true if the message type is enabled, false otherwise
         */
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
