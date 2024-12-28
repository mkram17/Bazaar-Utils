package com.github.sirmegabite.bazaarutils.configs;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.data.OptionSize;
import cc.polyfrost.oneconfig.config.migration.JsonMigrator;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class BUConfig extends Config {
    public static List<ItemData> watchedItems = new ArrayList<>();
    public static OneKeyBind pasting = new OneKeyBind(Keyboard.KEY_V, Keyboard.KEY_LCONTROL);

    public BUConfig(boolean enabled, boolean canToggle) {
        super(new Mod("BazaarUtils", ModType.SKYBLOCK,"/icon.png"), "/bazaarutils.json", enabled, canToggle);
        initialize();
    }
    @Switch(
            name = "Mod Enabled",
            size = OptionSize.DUAL, // optional, declares whether the element is single column or dual column
            category = "General", // optional
            subcategory = "Switches" // optional
    )
    public static boolean modEnabled = true;
    @Switch(
            name = "Auto Flip",
            size = OptionSize.SINGLE, // optional, declares whether the element is single column or dual column
            category = "General", // optional
            subcategory = "Switches", // optional
            description = "Automatically paste the right price into the flip order sign"
    )
    public static boolean autoFlip = true; // this is the default value.

    @HUD(name = "Developer",
            category = "Developer"
    )
    public Developer developer = new Developer("watched items");

}
