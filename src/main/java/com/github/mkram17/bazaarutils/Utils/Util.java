package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.BazaarData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Util {
    public enum notificationTypes {
        ERROR, GUI, FEATURE, BAZAARDATA, COMMAND, ITEMDATA;
        public boolean isEnabled() {
            return BUConfig.get().developer.isDeveloperVariableEnabled(this);
        }
    }
    public static final String HELPMESSAGE = "Commands: /bu or /bazaarutils to open settings gui. \n---------------------------\n " +
            "/bu customorders to see current Custom Orders. /bu customorder {order amount} {slot number} to make new Custom Order /bu customorder remove {customorder number} to remove Custom Order (find number by using /bu customorders) \n---------------------------\n  " +
            "For more help go to https://discord.gg/";

    public static<T> void notifyAll(T message) {
        String callingName = getCallingClassName();
        String messageStr = message.toString();
        messageStr = messageStr.toLowerCase().contains("exception") ? "§c" + messageStr : "§a" + messageStr;

            MinecraftClient.getInstance().player.sendMessage(Text.literal("[BazaarUtils] " + messageStr), false);
        LogManager.getLogger(callingName).info("[AutoBz] Message [" + message + "]");
    }

    public static<T> void notifyAll(T message, notificationTypes notiType) {
        String callingName = getCallingClassName();
        String simpleCallingName = callingName.substring(callingName.lastIndexOf(".") + 1);
        String messageStr = notiType == notificationTypes.ERROR ? "§c" + message : "§a" + message;

        if (notiType.isEnabled() || BUConfig.get().developer.allMessages) {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal("[" + simpleCallingName + "] " + messageStr), false);
            }
            LogManager.getLogger(callingName).info("[AutoBz] Message [" + message + "]");
        }
    }

    public static<T> void executeLater(Runnable runnable, int milliDelay){
        try {
            Thread.sleep(milliDelay);
            MinecraftClient.getInstance().execute(runnable);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void notifyChatCommand(String message, String command){
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.sendMessage(Text.literal(message)
                .styled(style -> style
                        .withBold(true)
                        .withColor(Formatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Run /" + command)))
                ), false);
    }


    public static void addWatchedItem(String itemName, Double price, boolean isSellOrder, int volume) {
        itemName = itemName.toLowerCase();
        if (BazaarData.findProductId(itemName) != null) {
            ItemData.priceTypes type = isSellOrder ? ItemData.priceTypes.INSTABUY : ItemData.priceTypes.INSTASELL;
            ItemData itemToAdd =new ItemData(itemName, price, type, volume);
            BUConfig.get().watchedItems.add(itemToAdd);
            notifyAll("Added item: § " + itemToAdd.getGeneralInfo());
        } else {
            notifyAll("Could not add item: § " + itemName + " §a (is it spelled correctly?)");
        }
        ItemData.update();
    }

    public static void startExecutors() {
        BazaarData.scheduleBazaar();
        ItemData.scheduleNotifyOutdated();
    }

    public static String getCallingClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        return stackTrace[3].getClassName().substring(stackTrace[3].getClassName().lastIndexOf(".") + 1);
    }

    public static void copyToClipboard(String clip) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(clip), null);
    }

    public static<T> void sendFirst(T itemToSend, int howLong, String info) {
        String output = itemToSend.toString().substring(0, Math.min(howLong, itemToSend.toString().length()));
        System.out.println(info != null ? info + output : output);
    }

    public static void copyItem(String itemName, ItemData.priceTypes priceType) {
        String productID = BazaarData.findProductId(itemName);
        double price = BazaarData.findItemPrice(productID, priceType);
        try {
            String clipValue = String.format("%.2f", price + (priceType == ItemData.priceTypes.INSTABUY ? -0.1 : 0.1));
            copyToClipboard(clipValue);
        } catch (Exception e) {
            Util.notifyAll("Failed to copy updated price to clipboard");
        }
    }

    public static String removeFormatting(String str) {
        return str.replaceAll("§.", "").replace(",", "").trim();
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

    public static String getClipboardContents() {
        try {
            return (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isSimilar(double d1, double d2) {
        return Math.abs(d1 - d2) < 0.01;
    }

    public static String capAtMinecraftLength(String input, int limit) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        return capAtLength(input, limit, c -> renderer.getWidth(String.valueOf(c)));
    }

    private static String capAtLength(String input, int limit, LengthJudger lengthJudger) {
        int currentLength = 0;
        int index = 0;
        for (char c : input.toCharArray()) {
            currentLength += lengthJudger.judgeLength(c);
            if (currentLength >= limit) break;
            index++;
        }
        return input.substring(0, index);
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

    @FunctionalInterface
    public interface LengthJudger {
        int judgeLength(char c);
    }
}