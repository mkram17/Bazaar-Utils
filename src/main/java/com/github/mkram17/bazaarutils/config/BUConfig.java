package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.features.CustomOrder;
import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;

public class BUConfig {
    public static ConfigClassHandler<BUConfig> HANDLER = ConfigClassHandler.createBuilder(BUConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("bazaarutils.json"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting) // not needed, pretty print by default
                    .setJson5(true)

                    .build())
            .build();

    public static void openConfig(){
        MinecraftClient.getInstance().setScreen(createGUI(MinecraftClient.getInstance().currentScreen));
        Util.notifyAll("Tried to open GUI", Util.notificationTypes.GUI);
    }

    public static Screen createGUI(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> {
            builder.title(Text.literal("Bazaar Utils"))
                    .category(CustomOrder.create());
            return builder;
        }).generateScreen(parent);
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> opt) {
        return BooleanControllerBuilder.create(opt).yesNoFormatter().coloured(true);
    }

    @SerialEntry
    public static ArrayList<ItemData> watchedItems = new ArrayList<>();

    @SerialEntry
    public static int outdatedTiming = 5;

    @SerialEntry
    public static boolean notifyOutdated = true;

    @SerialEntry
    public static boolean buyMaxEnabled = true;


    public static class Developer{
        public static boolean devMessages = false;
        public static boolean allMessages = false;
        public static boolean errorMessages = false;
        public static boolean guiMessages = false;
        public static boolean featureMessages = false;
        public static boolean bazaarDataMessages = false;
        public static boolean commandMessages = false;
        public static boolean itemDataMessages = false;

        public String getText(boolean example) {
            if(example) return "I'm in Example mode";
            if(!ItemData.nameList.isEmpty()) {
                ItemData.updateLists();
                return String.join(", ", ItemData.nameList);
            }
            else return "No watched items";
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
    }

    @SerialEntry(comment = "This string is amazing")
    public String myCoolString = "How amazing!";
}
