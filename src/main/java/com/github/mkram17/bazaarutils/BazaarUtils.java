package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.features.Bookmark;
import com.github.mkram17.bazaarutils.features.OrderStatusHighlight;
import com.github.mkram17.bazaarutils.features.StashHelper;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.Commands;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.mojang.serialization.Codec;
import de.siphalor.amecs.api.AmecsKeyBinding;
import lombok.Getter;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class BazaarUtils implements ClientModInitializer {
    public static IEventBus eventBus = new EventBus();
    public static StashHelper stashHelper;
    public static ArrayList<KeyBinding> keybinds = new ArrayList<>();
    public static final String MODID = "bazaarutils";
    public static boolean updatedMajorVersion = false;
    @Getter
    private static String updateNotes;


    //TODO combine both groups of listeners into one and just subscribe after handler load
    @Override
    public void onInitializeClient() {
        BUConfig.HANDLER.load();

        BUCompatibilityHelper.initializePatches();

        getModProperties();
        registerEventBus();
        subscribeEvents();
        registerCommands();
        registerKeybinds();
        setDefaultValues();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[BazaarUtils] Shutting down mod components");
            OrderStatusHighlight.shutdown();
        }, "BazaarUtils-Shutdown"));
    }

    private void registerEventBus() {
        eventBus.registerLambdaFactory("com.github.mkram17.bazaarutils", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Commands.register(dispatcher);
        });
    }
    //must be run after config load
    private void subscribeEvents(){
        BUListener.addTransientEvents();
        List<BUListener> listeners = BUListener.getTransientEvents();
        listeners.addAll(BUConfig.get().getSerializedEvents());

        for(BUListener listener : listeners) {
            listener.subscribe();
        }
    }

    private void registerKeybinds(){
        if(!BUCompatibilityHelper.isAmecsReborn())
            return;
        stashHelper = new StashHelper();
        stashHelper.registerTickCounter();
        keybinds.add(stashHelper);

        for(KeyBinding keybind : keybinds) {
            if(keybind instanceof AmecsKeyBinding)
                KeyBindingHelper.registerKeyBinding(keybind);
        }
    }

    private void setDefaultValues(){
        //causes errors if done as default in config bc constructor uses other config info which isnt loaded yet
        if(BUConfig.get().bookmarks.isEmpty()) {
            BUConfig.get().bookmarks.add(new Bookmark("Diamond", Items.DIAMOND.getDefaultStack()));
        }


    }
    private void getModProperties(){
        FabricLoader.getInstance().getModContainer(MODID).ifPresent(modContainer -> {
            ModMetadata metadata = modContainer.getMetadata();

            CustomValue updateNotesValue = metadata.getCustomValue("latestMajorUpdateNotes");
            if (updateNotesValue != null)
                updateNotes = updateNotesValue.getAsString();

            var oldVersion = BUConfig.get().MODVERSION;
            var currentVersion = metadata.getVersion().getFriendlyString();

            var oldVersionMajor = oldVersion.substring(oldVersion.indexOf(".")+1);
            var currentVersionMajor = currentVersion.substring(currentVersion.indexOf(".")+1);

            BUConfig.get().MODVERSION = currentVersion;
            BUConfig.HANDLER.save();

            if(!oldVersionMajor.equals(currentVersionMajor))
                updatedMajorVersion = true;
        });
    }


    public static final ComponentType<String> CUSTOM_SIZE_COMPONENT = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(BazaarUtils.MODID, "custom_size"),
            ComponentType.<String>builder().codec(Codec.STRING).build()
    );
    public static final ComponentType<Boolean> CUSTOM_SHOWPRICECHART_COMPONENT = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(BazaarUtils.MODID, "has_price_chart"),
            ComponentType.<Boolean>builder().codec(Codec.BOOL).build()
    );
}
