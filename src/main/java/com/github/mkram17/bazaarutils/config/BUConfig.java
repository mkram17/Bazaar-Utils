package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.features.*;
import com.github.mkram17.bazaarutils.features.FlipHelper.BiddingType;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSellControl;
import com.github.mkram17.bazaarutils.misc.adapters.ItemStackCodecGsonAdapter;
import com.github.mkram17.bazaarutils.misc.adapters.ZonedDateTimeAdapter;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class BUConfig {
    public static RuntimeTypeAdapterFactory<CustomOrder> customOrderAdapterFactory = RuntimeTypeAdapterFactory.of(CustomOrder.class)
            .registerSubtype(MaxBuyOrder.class)
            .registerSubtype(CustomOrder.class);


    public static final ConfigClassHandler<BUConfig> HANDLER = ConfigClassHandler.createBuilder(BUConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("bazaarutils.json"))
                    .appendGsonBuilder(gsonBuilder -> gsonBuilder
                            .registerTypeAdapter(ItemStack.class, new ItemStackCodecGsonAdapter())
                            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                            .registerTypeAdapterFactory(customOrderAdapterFactory))
                    .build())
            .build();

    public static BUConfig get() {
        return HANDLER.instance();
    }


    @SerialEntry
    public String MOD_VERSION = "";
    @SerialEntry
    public boolean firstLoad = true;
    @SerialEntry
    public String resourcesSha = "";
    @SerialEntry
    public FlipHelper flipHelper = new FlipHelper(true, BiddingType.COMPETITIVE, 17);
    @SerialEntry
    public List<BazaarOrder> userOrders = new CopyOnWriteArrayList<>(); // the user's orders
    @SerialEntry
    public double bzTax = 1.125;
    @SerialEntry
    public ArrayList<CustomOrder> customOrders = new ArrayList<>(List.of(new MaxBuyOrder(true)));
    @SerialEntry
    public boolean developerMode = false;
    @SerialEntry
    public OutbidOrderHandler outbidOrderHandler = new OutbidOrderHandler(false, true);
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
    public OrderLimit orderLimit = new OrderLimit(true, 15_000_000_000d);
    @SerialEntry
    public BazaarOpenOrdersButton bazaarOpenOrdersButton = new BazaarOpenOrdersButton(true);


    public static void openGUI() {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(BUConfigGui.create(null, get())));
    }

    public Screen createGUI(Screen parent) {
        return BUConfigGui.create(parent, this);
    }

     public List<BUListener> getSerializedEvents() {
         List<BUListener> events = new ArrayList<>();

         for (Field field : this.getClass().getDeclaredFields()) {
             field.setAccessible(true);
             try {
                 Object value = field.get(this);

                 if (value instanceof BUListener) {
                     events.add((BUListener) value);
                 } else if (value instanceof Collection) {
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

     public static List<AbstractWidget> getWidgets(){
        List<AbstractWidget> widgets = new ArrayList<>();
        //automatically added using @RegisterWidget annotation
        return widgets;
    }

    public static class Developer {
        public boolean allMessages = false;
        public boolean errorMessages = false;
        public boolean guiMessages = false;
        public boolean featureMessages = false;
        public boolean bazaarDataMessages = false;
        public boolean commandMessages = false;
        public boolean itemDataMessages = false;

        public Collection<? extends Option<?>> createOptions() {
            ArrayList<Option<?>> optionList = new ArrayList<>();
            optionList.add(Option.<Boolean>createBuilder()
                    .name(Component.literal("Error Messages"))
                    .binding(errorMessages,
                            () -> errorMessages,
                            newVal -> errorMessages = newVal)
                    .controller(BUConfigGui::createBooleanController)
                    .build());
            optionList.add(Option.<Boolean>createBuilder()
                    .name(Component.literal("GUI Messages"))
                    .binding(guiMessages,
                            () -> guiMessages,
                            newVal -> guiMessages = newVal)
                    .controller(BUConfigGui::createBooleanController)
                    .build());
            optionList.add(Option.<Boolean>createBuilder()
                    .name(Component.literal("Feature Messages"))
                    .binding(featureMessages,
                            () -> featureMessages,
                            newVal -> featureMessages = newVal)
                    .controller(BUConfigGui::createBooleanController)
                    .build());
            optionList.add(Option.<Boolean>createBuilder()
                    .name(Component.literal("Bazaar Data Messages"))
                    .binding(bazaarDataMessages,
                            () -> bazaarDataMessages,
                            newVal -> bazaarDataMessages = newVal)
                    .controller(BUConfigGui::createBooleanController)
                    .build());
            optionList.add(Option.<Boolean>createBuilder()
                    .name(Component.literal("Command Messages"))
                    .binding(commandMessages,
                            () -> commandMessages,
                            newVal -> commandMessages = newVal)
                    .controller(BUConfigGui::createBooleanController)
                    .build());
            optionList.add(Option.<Boolean>createBuilder()
                    .name(Component.literal("Item Data Messages"))
                    .binding(itemDataMessages,
                            () -> itemDataMessages,
                            newVal -> itemDataMessages = newVal)
                    .controller(BUConfigGui::createBooleanController)
                    .build());
            return optionList;
        }

        public boolean isDeveloperVariableEnabled(Util.notificationTypes type) {
            return switch (type) {
                case GUI -> guiMessages;
                case FEATURE -> featureMessages;
                case BAZAARDATA -> bazaarDataMessages;
                case COMMAND -> commandMessages;
                case ORDERDATA -> itemDataMessages;
            };
        }
    }
 }
