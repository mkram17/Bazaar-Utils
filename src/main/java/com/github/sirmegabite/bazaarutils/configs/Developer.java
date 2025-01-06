package com.github.sirmegabite.bazaarutils.configs;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.data.OptionSize;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;

public class Developer extends SingleTextHud {

    public Developer() {

        super("Watched items: ", true);
    }
    @Switch(
            name = "Developer Messages",
            size = OptionSize.SINGLE // optional, declares whether the element is single column or dual column
    )
    public static boolean devMessages = false;
    @Switch(
            name = "All Messages",
            size = OptionSize.SINGLE // optional, declares whether the element is single column or dual column
    )
    public static boolean allMessages = false;
    @Switch(
            name = "Error Messages",
            size = OptionSize.SINGLE
    )
    public static boolean errorMessages = false;
    @Switch(
            name = "Gui Messages",
            size = OptionSize.SINGLE
    )
    public static boolean guiMessages = false;
    @Switch(
            name = "Feature Messages",
            size = OptionSize.SINGLE
    )
    public static boolean featureMessages = false;
    @Switch(
            name = "Data Messages",
            size = OptionSize.SINGLE
    )
    public static boolean bazaarDataMessages = false;
    @Switch(
            name = "Command Messages",
            size = OptionSize.SINGLE
    )
    public static boolean commandMessages = false;
    @Switch(
            name = "Item Messages",
            size = OptionSize.SINGLE
    )
    public static boolean itemDataMessages = false;

    @Override
    public String getText(boolean example) {
        if(example) return "I'm in Example mode";
        if(!ItemData.names.isEmpty())
            return String.join(", ", ItemData.getNames());
        else return "No watched items";
    }

}