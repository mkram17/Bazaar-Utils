package com.github.sirmegabite.bazaarutils.configs;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Number;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.data.OptionSize;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import com.github.sirmegabite.bazaarutils.features.CustomOrder;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class BUConfig extends Config {
    public static List<ItemData> watchedItems = new ArrayList<>();
    public static OneKeyBind pasting = new OneKeyBind(Keyboard.KEY_V, Keyboard.KEY_LCONTROL);
    public static double bzTax = 0.01125;

    public BUConfig(boolean enabled, boolean canToggle) {
        super(new Mod("BazaarUtils", ModType.SKYBLOCK,"/icon.png"), "/bazaarutils.json", enabled, canToggle);
        initialize();
    }
    @Switch(
            name = "Auto Flip",
            size = OptionSize.SINGLE, // optional, declares whether the element is single column or dual column
            category = "General", // optional
            subcategory = "Switches", // optional
            description = "Automatically paste the right price into the flip order sign"
    )
    public static boolean autoFlip = true; // this is the default value.

    @Switch(
            name = "Buy Max Option",
            size = OptionSize.DUAL,
            description = "Give an option to buy the maximum amount (71680) of an item in buy order screen."
    )
    public static boolean buyMaxEnabled = true;

    @Switch(
            name = "Notify Outdated",
            description = "Notifies you when your buy or sell orders get outbid"
    )
    public static boolean notifyOutdated = true;

    @Number(
            name = "Notify Outdated Timing",
            description = "The time between each time you will be notified in minutes",
            min = 1,
            max = 60
    )
    public static int outdatedTiming = 5;

    @HUD(name = "Developer",
            category = "Developer"
    )
//    public Developer developer = new Developer();
    public Developer developer = new Developer();



}
