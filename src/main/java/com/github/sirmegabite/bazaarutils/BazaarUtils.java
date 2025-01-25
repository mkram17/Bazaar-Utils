package com.github.sirmegabite.bazaarutils;

import cc.polyfrost.oneconfig.utils.commands.CommandManager;
import com.github.sirmegabite.bazaarutils.EventHandlers.ChestLoadedEvent;
import com.github.sirmegabite.bazaarutils.EventHandlers.EventHandler;
import com.github.sirmegabite.bazaarutils.EventHandlers.SignOpenEvent;
import com.github.sirmegabite.bazaarutils.Utils.Commands;
import com.github.sirmegabite.bazaarutils.Utils.GUIUtils;
import com.github.sirmegabite.bazaarutils.Utils.Util;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import com.github.sirmegabite.bazaarutils.features.AutoFlipper;
import com.github.sirmegabite.bazaarutils.features.CustomOrder;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = BazaarUtils.MODID, version = BazaarUtils.VERSION)
public class BazaarUtils {
    //product id is what its called in json file, product name is the natural language version of it
    public static final String MODID = "bazaarutils";
    public static final String NAME = "Bazaar Utils";
    public static final String VERSION = "0.0.1";
    public static GUIUtils gui;
    public static BUConfig config;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        registerEventUsers();
        CommandManager.register(new Commands());
        Util.startExecutors();
        config = new BUConfig(true, true);

    }
    public void preinit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);

    }

    private void registerEventUsers(){
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        MinecraftForge.EVENT_BUS.register(new AutoFlipper());
        MinecraftForge.EVENT_BUS.register(new GUIUtils());
        MinecraftForge.EVENT_BUS.register(new ChestLoadedEvent());
        MinecraftForge.EVENT_BUS.register(new SignOpenEvent());
        MinecraftForge.EVENT_BUS.register(new CustomOrder(() -> BUConfig.buyMaxEnabled, () -> 71680, () -> 17, 10));
        MinecraftForge.EVENT_BUS.register(new CustomOrder(() -> BUConfig.buyCustomEnabled, () -> BUConfig.buyCustomAmount, () -> BUConfig.buyCustomSlot, 3));
        MinecraftForge.EVENT_BUS.register(this);

    }
}