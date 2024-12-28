package com.github.sirmegabite.bazaarutils.configs;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.Hud;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import cc.polyfrost.oneconfig.hud.TextHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;

import java.util.List;

public class Developer extends SingleTextHud {


    public Developer(String title) {

        super(title, true);
        this.title = title;
    }

    // this method is called every ingame tick. It is used to update the text shown
    // on the hud.
    @Override
    public String getText(boolean example) {
        if(example) return "I'm in Example mode";
        return String.join(", ", ItemData.getNames());
    }
}