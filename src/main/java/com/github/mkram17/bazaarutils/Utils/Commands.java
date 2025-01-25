package com.github.mkram17.bazaarutils.Utils;

import cc.polyfrost.oneconfig.utils.commands.annotations.*;
import com.github.mkram17.bazaarutils.BazaarUtils;

import static com.github.mkram17.bazaarutils.configs.BUConfig.watchedItems;

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