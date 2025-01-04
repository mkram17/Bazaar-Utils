package com.github.sirmegabite.bazaarutils.configs;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;

import java.util.List;

public class Developer extends SingleTextHud {

    public Developer() {

        super("Watched items: ", true);
    }

    @Override
    public String getText(boolean example) {
        if(example) return "I'm in Example mode";
        if(!ItemData.names.isEmpty())
            return String.join(", ", ItemData.getNames());
        else return "No watched items";
    }
}