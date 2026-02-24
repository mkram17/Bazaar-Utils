package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.features.DisableErrorNotifications;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import lombok.AllArgsConstructor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//main utility class. More specific utility classes are in utils package
public class Util {


    private static final LinkedList<ScheduledTask> tasks = new LinkedList<>();
    public static final String HELP_MESSAGE = "Commands: /bu or /bazaarutils to open settings gui. \n---------------------------\n " +
            "/bu tax {amount} to set bazaar tax. This is important for the mod to function correctly. /bu customorders to see current Custom Orders. /bu customorder {order amount} {slot number} to make new Custom Order /bu customorder remove {customorder number} to remove Custom Order (find number by using /bu customorders) \n---------------------------\n  ";
    public static final String DISCORD_LINK = "https://discord.gg/xDKjvm5hQd";
    public static final Text DISCORD_TEXT = Text.literal("Discord server")
            .styled(style -> {
                try {
                    return style
                            .withBold(true)
                            .withClickEvent(new ClickEvent.OpenUrl(new URI(DISCORD_LINK)))
                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to join the Discord!")));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            });
    public static final Text CHANGELOG = Text.literal("Click To See Changelog")
            .styled(style -> {
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

    public static void logMessage(String message) {
        String callingName = getCallingClassName();
        LogManager.getLogger(callingName).info("[" + BazaarUtils.MOD_NAME + "] Message [{}]", message);
    }
    public static void logError(String message, Throwable e) {
        String callingName = getCallingClassName();
        logError(message, callingName, e);
    }

    private static void logError(String message, String callingName, Throwable e) {
        if(e == null) {
            LogManager.getLogger(callingName).error("[" + BazaarUtils.MOD_NAME + " Error]({}) Developer Message: {}", callingName, message);
        } else {
            LogManager.getLogger(callingName).error("[" + BazaarUtils.MOD_NAME + " Error]({}) Developer Message: {}\n Throwable Message {}\n Stacktrace: {}", callingName, message, e.getMessage(), Arrays.toString(e.getStackTrace()));
        }
    }

    public static void notifyError(String message, Throwable e) {
        String callingName = getCallingClassName();
        String simpleCallingName = callingName.substring(callingName.lastIndexOf(".") + 1);
        Text messageText = Text.literal("[" + BazaarUtils.MOD_NAME + " Error]: " + message + ". Click here for support.")
                .styled(style -> {
                    try {
                        return style.withColor(Formatting.RED)
                                .withClickEvent(new ClickEvent.OpenUrl(new URI("https://discord.gg/xDKjvm5hQd")))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to join the Discord for support")));
                    } catch (URISyntaxException uriSyntaxException) {
                        throw new RuntimeException(uriSyntaxException);
                    }
                });

        if (!DisableErrorNotifications.isEnabled()) {
            PlayerActionUtil.sendPlayerMessage(messageText);
        }

        logError(message,simpleCallingName, e);
    }


    public static void addWatchedOrder(Order item){
        if(item == null)
            return;
        assert item.getProductID() != null;
        BUConfig.get().general.userOrders.add(item);
        PlayerActionUtil.notifyAll("Added item: § " + item, NotificationType.ORDERDATA);
        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.ADD, item));
        ConfigUtil.scheduleConfigSave();
    }

    @RunOnInit
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

    @AllArgsConstructor
    private static class ScheduledTask {
        int ticksLeft;
        Runnable action;
    }

    //this one runs asynch and other one runs on main thread (i think)
    public static void tickExecuteLater(int ticks, Runnable action) {
        synchronized (tasks) {
            tasks.add(new ScheduledTask(ticks, action));
        }
    }

    public static String getCallingClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 3) {
            String className = stackTrace[3].getClassName();
            return className.substring(className.lastIndexOf(".") + 1);
        }
        return "UnknownClass";
    }
    public static boolean genericIsSimilarValue(double value1, double value2, double tolerance) {
        return Math.abs(value1 - value2) <= tolerance;
    }
    //finds the first index that contains lookingFor, so there could be another later which would cause problems
    public static int componentIndexOf(List<Text> components, String lookingFor){
        int num = 0;
        for(Text component : components){
            if(component.getString().contains(lookingFor))
                return num;
            num++;
        }
            return -1;
    }
    public static int componentLastIndexOf(List<Text> components, String lookingFor){
        for (int i = components.size() - 1; i >= 0; i--) {
            if (components.get(i).getString().contains(lookingFor)) {
                return i;
            }
        }
        return -1;
    }
    @Nullable
    public static Text findComponentWith(List<Text> components, String lookingFor){
        for(Text component : components){
            if(component.getString().contains(lookingFor))
                return component;
        }
            return null;
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

    public static String getPrettyString(double num) {
        return String.format("%,.1f", num);
    }

    public static double truncateNum(double num) {
        return Math.round(num * 10.0) / 10.0;
    }

}