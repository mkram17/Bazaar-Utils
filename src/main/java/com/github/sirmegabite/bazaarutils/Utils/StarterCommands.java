package com.github.sirmegabite.bazaarutils.Utils;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.github.sirmegabite.bazaarutils.configs.BUConfig.watchedItems;

public class StarterCommands extends CommandBase {
    @Override
    public String getCommandName() {
        return "autobazaar";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/autobz <subcommand> <arguments>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args){
        Util.notifyConsole("Command: autobz" + "Command received: " + Arrays.toString(args) + " args length: " + args.length);
        if (args.length > 0) {
            //to start the mod

            if (args[0].equals("start")) {
                if (!BUConfig.modEnabled) {
                    BUConfig.modEnabled = true;
                    Util.notifyAll("§aMod enabled");
                } else {
                    Util.notifyAll("§aMod already enabled!");
                }
            }
            //to stop the mod
            if (args[0].equals("stop")) {
                if (BUConfig.modEnabled) {
                    BUConfig.modEnabled = false;
                    Util.notifyAll("§aMod disabled");
                } else {
                    Util.notifyAll("§aMod already disabled!");
                }
            }


            //to add watched bazaar items
            if (args.length > 1 && args[0].equalsIgnoreCase("add")) {
                if (args[0].equals("add")) {
                    String item = String.join(" ", Arrays.asList(args).subList(1, args.length));
                    //adds -1 when you are adding it yourself -- eventually you won't be able to add watched item by command so this code will be removed.
                    Util.addWatchedItem(item, Double.parseDouble(args[1]), true, -1);

                }
                //uses reflection to get variable values while playing minecraft (must remove in build)
                if (args[0].equals("dev")) {
                    Util.notifyConsole("Tried to do dev stuff " + "(" + args[1] + ")");
                    try {
                        Field field = BazaarUtils.class.getDeclaredField(args[1]);
                        field.setAccessible(true);
                        Object value = field.get(null);  // Assuming the field is static
                        Util.notifyAll(args[1] + ": " + value);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        Util.notifyAll("Error: Could not access " + args[1]);
                    }
                }
            }
        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("autobazaar");
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1)
            //must remove dev in build
            return getListOfStringsMatchingLastWord(args, "start", "stop", "add", "prices");
        if (args.length >= 2 && args[1].equals("add"))
            return getListOfStringsMatchingLastWord(args, watchedItems);

        return Collections.emptyList();
    }

}
