package com.github.sirmegabite.bazaarutils.Utils;

import cc.polyfrost.oneconfig.gui.OneConfigGui;
import cc.polyfrost.oneconfig.libs.universal.ChatColor;
import cc.polyfrost.oneconfig.libs.universal.UChat;
import cc.polyfrost.oneconfig.utils.commands.annotations.*;
import cc.polyfrost.oneconfig.utils.gui.GuiUtils;
import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;

import static com.github.sirmegabite.bazaarutils.configs.BUConfig.watchedItems;

@Command(value = "bazaarutils", aliases = {"bu"})
public class Commands{


    @Main(description = "Opens the Bazaar Utils GUI")
    private void main() {
        BazaarUtils.config.openGui();
    }

    @SubCommand(description = "Remove watched item")
    private void remove(@Description(autoCompletesTo = {"remove"}, description = "array index") int arg) {
        Util.notifyAll("Removed " + watchedItems.get(arg).getGeneralInfo(), Util.notificationTypes.COMMAND);
        ItemData.removeItem(ItemData.getItem(arg));
    }

    @SubCommand(description = "Get general info")
    private void info(@Description(autoCompletesTo = {"info"}) int arg){
        Util.notifyAll(watchedItems.get(arg).getGeneralInfo());
    }



}