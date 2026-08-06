package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import lombok.AllArgsConstructor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import org.apache.logging.log4j.LogManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Util {
    private static final LinkedList<ScheduledTask> tasks = new LinkedList<>();

    public static final String HELP_MESSAGE = "Commands: /bu or /bazaarutils to open settings gui. \n---------------------------\n " +
            "/bu tax {amount} to set bazaar tax. This is important for the mod to function correctly. /bu customorders to see current Custom Orders. /bu customorder {order amount} {slot number} to make new Custom Order /bu customorder remove {customorder number} to remove Custom Order (find number by using /bu customorders) \n---------------------------\n  ";
    public static final String DISCORD_LINK = "https://discord.gg/xDKjvm5hQd";

    public static final String CHANGELOG_LINK = "https://modrinth.com/mod/bazaar-utils/changelog";

    /**
     * Text that opens {@code url} when clicked, hovering with {@code hoverText}.
     *
     * <p>{@link URI#create} rather than the checked constructor: every URL passed here is a
     * literal in this file, so a malformed one is a bug to surface at class load — not something
     * for each call site to wrap in its own try/catch.</p>
     */
    public static MutableComponent link(String text, String url, String hoverText) {
        return Component.literal(text).withStyle(style -> style
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
    }

    public static final Component DISCORD_TEXT = link("Discord server", DISCORD_LINK, "Click to join the Discord!")
            .withStyle(ChatFormatting.BOLD);

    public static final Component CHANGELOG = link("Click To See Changelog", CHANGELOG_LINK, "Click to see the changelog")
            .withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN);

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
        Component messageText = link(
                "[" + BazaarUtils.MOD_NAME + " Error]: " + message + ". Click here for support.",
                DISCORD_LINK,
                "Click to join the Discord for support"
        ).withStyle(ChatFormatting.RED);

        if (!BazaarUtilsModules.DisableErrorNotifications.isEnabled()) {
            PlayerActionUtil.sendPlayerMessage(messageText);
        }

        logError(message, simpleCallingName, e);
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