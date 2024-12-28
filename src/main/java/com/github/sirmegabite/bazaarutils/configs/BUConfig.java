package com.github.sirmegabite.bazaarutils.configs;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.data.OptionSize;
import cc.polyfrost.oneconfig.config.migration.JsonMigrator;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;

import java.util.ArrayList;
import java.util.List;

public class BUConfig extends Config {
    public static List<ItemData> watchedItems = new ArrayList<>();
    public static boolean ctrlDown = false;
    public static boolean vDown = false;

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
    public static boolean modEnabled = true; // this is the default value.

    @HUD(name = "Developer",
            category = "Developer"
    )
    public Developer developer = new Developer("watched items");

}
