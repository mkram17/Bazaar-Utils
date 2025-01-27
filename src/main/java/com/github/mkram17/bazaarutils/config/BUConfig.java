package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;

import static com.github.mkram17.bazaarutils.Utils.Util.notificationTypes.*;

public class BUConfig {
    public static ConfigClassHandler<BUConfig> HANDLER = ConfigClassHandler.createBuilder(BUConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("bazaarutils.json"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting) // not needed, pretty print by default
                    .setJson5(true)
                    .build())
            .build();

    public void openGui(){
        YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Bazaar Utils"))
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Auto Flip"))
                        .tooltip(Text.literal("Automatically paste the right price into the flip order sign"))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Name of the group"))
                                .description(OptionDescription.of(Text.literal("This text will appear when you hover over the name or focus on the collapse button with Tab.")))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Boolean Option"))
                                        .description(OptionDescription.of(Text.literal("This text will appear as a tooltip when you hover over the option.")))
                                        .binding(true, () -> this.myCoolBoolean, newVal -> this.myCoolBoolean = newVal)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())
                        .build())
                .build();
    }
    @SerialEntry
    public static ArrayList<ItemData> watchedItems = new ArrayList<>();

    @SerialEntry
    public static int outdatedTiming = 5;

    @SerialEntry
    public static boolean notifyOutdated = true;

    @SerialEntry
    public boolean myCoolBoolean = true;

    @SerialEntry
    public int myCoolInteger = 5;

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
