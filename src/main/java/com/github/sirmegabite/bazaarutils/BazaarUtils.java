package com.github.sirmegabite.bazaarutils;

import com.github.sirmegabite.bazaarutils.EventHandlers.EventHandlers;
import com.github.sirmegabite.bazaarutils.Utils.BazaarData;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import com.github.sirmegabite.bazaarutils.Utils.StarterCommands;
import net.minecraft.inventory.ContainerChest;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import java.util.ArrayList;
import java.util.List;

@Mod(modid = BazaarUtils.MODID, version = BazaarUtils.VERSION)
public class BazaarUtils {
    //product id is what its called in json file, product name is the natural language version of it
    public static final String MODID = "bazaarutils";
    public static final String NAME = "Bazaar Utils";
    public static final String VERSION = "0.0.1";
    public static boolean modEnabled = true;
    public static List<ItemData> watchedItems = new ArrayList<>();
    public static ContainerChest container;
    public static boolean ctrlDown = false;
    public static boolean vDown = false;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new EventHandlers());
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new StarterCommands());
        BazaarData.scheduleBazaar();

    }
    public void preinit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }
}