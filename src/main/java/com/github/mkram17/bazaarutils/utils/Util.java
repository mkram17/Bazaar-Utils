package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.UserOrdersChangeEvent;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

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
        if (e == null) {
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

        if (!BazaarUtilsModules.DisableErrorNotifications.isEnabled()) {
            PlayerActionUtil.sendPlayerMessage(messageText);
        }

        logError(message, simpleCallingName, e);
    }

    public static void addWatchedOrder(Order item) {
        if (item == null) return;
        assert item.getProductID() != null;
        UserOrdersStorage.INSTANCE.get().add(item);
        PlayerActionUtil.notifyAll("Added item: § " + item, NotificationType.ORDERDATA);
        EVENT_BUS.post(new UserOrdersChangeEvent(UserOrdersChangeEvent.ChangeTypes.ADD, item));
        UserOrdersStorage.INSTANCE.save();
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

    public static String stripFormatCodes(String s) {
        return s.replaceAll("§.", "");
    }

    public static String removeFormatting(String s) {
        return stripFormatCodes(s).replace(",", "").trim();
    }

    public static int parseNumber(String input) {
        input = input.toUpperCase();
        double value = Double.parseDouble(input.replaceAll("[^0-9.]", ""));

        if (input.endsWith("K")) return (int) (value * 1_000);
        if (input.endsWith("M")) return (int) (value * 1_000_000);
        if (input.endsWith("B")) return (int) (value * 1_000_000_000);

        return (int) value;
    }

    public static String formatNumberWithPrefix(double number) {
        String prefix;
        double value;

        if (number >= 1_000_000_000) {
            prefix = "B";
            value = number / 1_000_000_000.0;
        } else {
            prefix = "M";
            value = number / 1_000_000.0;
        }

        return String.format("%.2f", value) + prefix;
    }

    public static String extractTextAfterWord(String text, String word) {
        if (text == null || word == null || text.isEmpty() || word.isEmpty()) return "";

        int wordIndex = text.indexOf(word);
        if (wordIndex == -1) {
            return ""; // Word not found
        }

        int startIndex = wordIndex + word.length();
        if (startIndex >= text.length()) {
            return ""; // Word is at the end of the text
        }

        while (startIndex < text.length() && Character.isWhitespace(text.charAt(startIndex))) {
            startIndex++;
        }

        if (startIndex >= text.length()) {
            return ""; // No non-space characters after the word
        }

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
        return BigDecimal.valueOf(num)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}