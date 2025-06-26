package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import dev.isxander.yacl3.api.Option;
import lombok.AllArgsConstructor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Util implements BUListener {
    public static void sendCommand(String command){
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    @Override
    public void subscribe() {
        subscribeTicks();
    }

    public enum notificationTypes {GUI, FEATURE, BAZAARDATA, COMMAND, ITEMDATA;
        public boolean isEnabled() {
            return BUConfig.get().developer.isDeveloperVariableEnabled(this);
        }
    }
    private static final LinkedList<ScheduledTask> tasks = new LinkedList<>();
    public static final String HELP_MESSAGE = "Commands: /bu or /bazaarutils to open settings gui. \n---------------------------\n " +
            "/bu tax {amount} to set bazaar tax. This is important for the mod to function correctly. /bu customorders to see current Custom Orders. /bu customorder {order amount} {slot number} to make new Custom Order /bu customorder remove {customorder number} to remove Custom Order (find number by using /bu customorders) \n---------------------------\n  ";
    public static final String DISCORD_LINK = "https://discord.gg/xDKjvm5hQd";
    public static final Text DISCORD_TEXT = Text.literal("Discord server")
            .styled(style -> {
                        //? if > 1.21.4 {
                        try {
                            return style
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent.OpenUrl(new URI(DISCORD_LINK)))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to join the Discord!")));
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    //?} else {
                        /*return style
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://discord.gg/xDKjvm5hQd"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to join the Discord!")));
                    });
        *///?}
        public static final Text CHANGELOG = Text.literal("Click To See Changelog")
            .styled(style -> {
                        //? if > 1.21.4 {
                        try {
                            return style
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent.OpenUrl(new URI("https://modrinth.com/mod/bazaar-utils/changelog")))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to see the changelog")))
                                    .withFormatting(Formatting.GREEN);
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    //?} else {
                        /*return style
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/bazaar-utils/changelog"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to see the changelog")))
                                .withFormatting(Formatting.GREEN);
                    });
        *///?}

    public static void notifyAll(String message) {

        MutableText messageText = Text.literal("[Bazaar Utils] ").formatted(Formatting.GOLD);
        messageText.append(Text.literal(message).formatted(Formatting.WHITE));

        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(messageText, false);
        logMessage(message);
    }

    public static void logMessage(String message) {
        String callingName = getCallingClassName();
        LogManager.getLogger(callingName).info("[Bazaar Utils] Message [" + message + "]");
    }

    public static void notifyError(String message, Throwable e) {
        String callingName = getCallingClassName();
        String simpleCallingName = callingName.substring(callingName.lastIndexOf(".") + 1);
        Text messageText = Text.literal("[Bazaar Utils Error]: " + message + ". Click here for support.")
                .styled(style -> {
                    //? if > 1.21.4 {
                    try {
                        return style.withColor(Formatting.RED)
                                .withClickEvent(new ClickEvent.OpenUrl(new URI("https://discord.gg/xDKjvm5hQd")))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to join the Discord for support")));
                    } catch (URISyntaxException uriSyntaxException) {
                        throw new RuntimeException(uriSyntaxException);
                    }
                });
        //?} else {
                        /*return style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://discord.gg/xDKjvm5hQd"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to join the Discord for support")));
                    });
            *///?}

        if (MinecraftClient.getInstance().player != null && !BUConfig.get().disableErrorNotifications)
            MinecraftClient.getInstance().player.sendMessage(messageText, false);

        LogManager.getLogger(callingName).error("[Bazaar Utils Error] (" + callingName + ") "  + "Message: " + message);
        if(e != null){
            LogManager.getLogger(callingName).error("[Bazaar Utils Error]: " + e.getMessage() + " Stacktrace: " + Arrays.toString(e.getStackTrace()));
        }
    }

    public static void notifyAll(String message, notificationTypes notiType) {
        String callingName = getCallingClassName();
        String simpleCallingName = callingName.substring(callingName.lastIndexOf(".") + 1);
        var messageText = Text.literal("[" + simpleCallingName + "] ").formatted(Formatting.GOLD).append(Text.literal(message).formatted(Formatting.DARK_GREEN));

        if (!BUConfig.get().developerMode || (!notiType.isEnabled() && !BUConfig.get().developer.allMessages)) {
            if (BUConfig.get().developerMode) {
                logMessage(message);
            }
            return;
        }

//            LogManager.getLogger(callingName).info("[Bazaar Utils] watchedItems state: " + BUConfig.get().watchedItems);

            if (MinecraftClient.getInstance().player != null)
                MinecraftClient.getInstance().player.sendMessage(messageText, false);
            else
                notifyError("Could not send notification because player is null. Message: " + message, null);
    }


    public static void notifyChatCommand(MutableText message, String command){
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(message
                    .styled(style -> style
                                    //? if > 1.21.4 {
                                    .withClickEvent(new ClickEvent.RunCommand("/" + command))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Run /" + command)))
                            //?} else {
                                /*.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Run /" + command)))
                        *///?}
                    ), false);
        } else {
            // Queue the notification to be sent when player is available
            tickExecuteLater(20, () -> {
                if (MinecraftClient.getInstance().player != null) {
                    notifyChatCommand(message, command);
                }
            });
        }
    }

    public static void addWatchedItem(OrderData item){
        if(item == null)
            return;
        assert item.getProductID() != null;
        BUConfig.get().watchedOrders.add(item);
        notifyAll("Added item: § " + item.getGeneralInfo(), notificationTypes.ITEMDATA);
        BUConfig.HANDLER.save();
        OrderData.updateOutdatedItems();
    }

    public static void subscribeTicks() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            List<Runnable> actionsToRun = new LinkedList<>();
            List<ScheduledTask> tasksToRemove = new LinkedList<>();

            synchronized (tasks) {
                for (ScheduledTask task : tasks) {
                    task.ticksLeft--;
                    if (task.ticksLeft <= 0) {
                        actionsToRun.add(task.action);
                        tasksToRemove.add(task);
                    }
                }
                if (!tasksToRemove.isEmpty()) {
                    tasks.removeAll(tasksToRemove);
                }
            }

            // Run actions outside the synchronized block to prevent re-entrant modification
            for (Runnable action : actionsToRun) {
                try {
                    action.run();
                } catch (Exception e) {
                    notifyError("Error executing scheduled task", e);
                }
            }
        });
    }


    //this one runs asynch and other one runs on main thread (i think)
    public static void tickExecuteLater(int ticks, Runnable action) {
        synchronized (tasks) {
            tasks.add(new ScheduledTask(ticks, action));
        }
    }

    @AllArgsConstructor
    private static class ScheduledTask {
        int ticksLeft;
        Runnable action;
    }


    public static String getCallingClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 3) {
            String className = stackTrace[3].getClassName();
            return className.substring(className.lastIndexOf(".") + 1);
        }
        return "UnknownClass";
    }

    //finds the first index that contains lookingFor, so there could be another later which would cause problems
    public static int findComponentIndex(List<Text> components, String lookingFor){
        int num = 0;
        for(Text component : components){
            if(component.getString().contains(lookingFor))
                return num;
            num++;
        }
            return -1;
    }
    public static String findComponentWith(List<Text> components, String lookingFor){
        for(Text component : components){
            if(component.getString().contains(lookingFor))
                return component.getString();
        }
            return null;
    }

    public static void copyToClipboard(String clip) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(clip), null);
    }

    public static String removeFormatting(String str) {
        return str.replaceAll("§.", "").replace(",", "").trim();
    }
    public static int parseNumber(String input) {
        input = input.toUpperCase();
        double value = Double.parseDouble(input.replaceAll("[^0-9.]", ""));

        if (input.endsWith("K")) return (int) (value * 1_000);
        if (input.endsWith("M")) return (int) (value * 1_000_000);
        if (input.endsWith("B")) return (int) (value * 1_000_000_000);

        return (int) value;
    }
    public static <T> void writeFile(T content) {
        try {
            Files.write(Paths.get("bazaar_data.json"), content.toString().getBytes());
            notifyAll("Data written to file successfully.");
        } catch (Exception e) {
            System.out.println("Failed to write data to file");
            e.printStackTrace();
        }
    }

    public static String extractTextAfterWord(String text, String word) {
        if (text == null || word == null || text.isEmpty() || word.isEmpty()) {
            return "";
        }

        int wordIndex = text.indexOf(word);
        if (wordIndex == -1) {
            return ""; // Word not found
        }

        // Start looking after the word
        int startIndex = wordIndex + word.length();
        if (startIndex >= text.length()) {
            return ""; // Word is at the end of the text
        }

        // Skip spaces after the word
        while (startIndex < text.length() && Character.isWhitespace(text.charAt(startIndex))) {
            startIndex++;
        }

        if (startIndex >= text.length()) {
            return ""; // No non-space characters after the word
        }

        // Find the next space after non-space content
        int endIndex = startIndex;
        while (endIndex < text.length() && !Character.isWhitespace(text.charAt(endIndex))) {
            endIndex++;
        }

        return removeFormatting(text.substring(startIndex, endIndex));
    }
    public static double removeTrailingZeroes(double value) {
        return Double.parseDouble(String.valueOf(value).replaceAll("\\.0$", "").replaceAll("(\\.\\d*?)0+$", "$1"));
    }

    public static double truncateNumber(double number) {
        return Math.round(number * 100) / 100.0;
    }

    public static double getPrettyNumber(double num) {
        return truncateNumber(removeTrailingZeroes(num));
    }

}