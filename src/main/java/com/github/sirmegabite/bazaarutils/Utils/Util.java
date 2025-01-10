package com.github.sirmegabite.bazaarutils.Utils;

import com.github.sirmegabite.bazaarutils.configs.Developer;
import com.github.sirmegabite.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.sirmegabite.bazaarutils.configs.BUConfig.watchedItems;

public class Util {
    public enum notificationTypes {ERROR, GUI, FEATURE, BAZAARDATA, COMMAND, ITEMDATA;
        private boolean isEnabled;
        static {
            ERROR.isEnabled = Developer.errorMessages;
            GUI.isEnabled = Developer.guiMessages;
            FEATURE.isEnabled = Developer.featureMessages;
            BAZAARDATA.isEnabled = Developer.bazaarDataMessages;
            COMMAND.isEnabled = Developer.commandMessages;
            ITEMDATA.isEnabled = Developer.itemDataMessages;
        }
        public boolean isEnabled(){
            return Developer.isDeveloperVariableEnabled(this);
        }

    }
    public static<T> void notifyAll(T message) {
        String callingName = getCallingClassName();
        String messageStr = message.toString();
        if(messageStr.toLowerCase().contains("exception"))
            messageStr = "§c" + messageStr;
        else
            messageStr = "§a" + messageStr;
        if(Developer.devMessages)
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("[" + callingName + "] " + messageStr));
        LogManager.getLogger(callingName).info("[AutoBz] Message [" + message + "]");
    }
    public static<T> void notifyAll(T message, notificationTypes notiType) {
        String callingName = getCallingClassName();
        String simpleCallingName = callingName.substring(callingName.lastIndexOf(".")+1);
        String messageStr = message.toString();
        messageStr = "§a" + messageStr;
        if(notiType == notificationTypes.ERROR)
            messageStr = "§c" + messageStr;

        if(Developer.devMessages && (notiType.isEnabled() || Developer.allMessages)) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("[" + simpleCallingName + "] " + messageStr));
            LogManager.getLogger(callingName).info("[AutoBz] Message [" + message + "]");
        }
    }

    public static void addWatchedItem(String itemName, Double price, boolean isSellOrder, int volume){
        itemName = itemName.toLowerCase();
        if(BazaarData.findProductId(itemName) != null) {
            if(isSellOrder)
                watchedItems.add(new ItemData(itemName, price, ItemData.priceTypes.INSTABUY, volume));
            else
                watchedItems.add(new ItemData(itemName, price, ItemData.priceTypes.INSTASELL, volume));
            notifyAll("Added item: § " + itemName);
        } else {
            notifyAll("Could not add item: § " + itemName + " §a (is it spelled correctly?)");
        }
        ItemData.update();
    }
    public static String getCallingClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = stackTrace[3].getClassName();
        return className.substring(className.lastIndexOf(".")+1);
    }

    public static void copyToClipboard(String clip){
        StringSelection selection = new StringSelection(clip);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }

    //util to send the first characters of something, with info in front of it
    public static<T> void sendFirst(T itemToSend, int howLong, String info){
        if(info == null)
            System.out.println(itemToSend.toString().substring(0, howLong));
        else
            System.out.println(info + itemToSend.toString().substring(0, howLong));
    }
    public static void copyItem(String itemName, ItemData.priceTypes priceType){
        String productID = BazaarData.findProductId(itemName);
        double price = BazaarData.findItemPrice(productID, priceType);
            try {
                if(priceType == ItemData.priceTypes.INSTABUY){
                    copyToClipboard(Double.toString(price-.1));
                }else{
                    copyToClipboard(Double.toString(price+.1));
                }
            } catch (Exception e){
                Util.notifyAll("Failed to copy updated buy price to clipboard");
            }
        }

    public static String removeFormatting(String str){
        while(str.contains("§")){
            int index = str.indexOf("§");
            if (index + 1 < str.length()) {
                str = str.substring(0, index) + str.substring(index + 2);
            } else {
                str = str.substring(0, index);
            }
        }
        str = str.replace(",", "");
        return str;
    }

    public <T> void writeFile(T print) {
        //assumes that jsonString is already set with getBazaarJson future
//        System.out.println("Writing data to file...");
        try{
            Files.write(Paths.get(""), print.toString().getBytes());
            Util.notifyAll("Data written to file successfully.");
//            Util.sendFirst(jsonString, 500, "Written to file: ");
        }catch (Exception e){
            System.out.println("Failed to write data to file");
            e.printStackTrace();
        }
    }

    //thanks to SkyHanni
    public static void addToSign(String info, GuiScreen thisGui){
        IChatComponent[] lines = ((AccessorGuiEditSign) thisGui).getTileSign().signText;
        int index = ((AccessorGuiEditSign) thisGui).getEditLine();
        String text = lines[index].getUnformattedText() + info;
        lines[index] = new ChatComponentText(capAtMinecraftLength(text,91));
    }

    public static String getClipboardContents() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
    }
    public static boolean isSimilar(double d1, double d2){
        double factor = Math.pow(10, 2);
        return Math.round(d1 * factor) == Math.round(d2 * factor);

    }
    public static String capAtMinecraftLength(String input, int limit) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRendererObj;
        return capAtLength(input, limit, fontRenderer::getCharWidth);
    }

    private static String capAtLength(String input, int limit, LengthJudger lengthJudger) {
        int currentLength = 0;
        int index = 0;

        for (char c : input.toCharArray()) {
            currentLength += lengthJudger.judgeLength(c);
            if (currentLength >= limit) {
                break;
            }
            index++;
        }

        return input.substring(0, index);
    }
    public static double removeTrailingZeroes(double value) {
        return Double.parseDouble(String.valueOf(value).replaceFirst("\\.?0*$", ""));
    }
    public static double truncateNumber(double number) {
        return Double.parseDouble(String.format("%.2f", number));
    }
    public static double getPrettyNumber(double num){
        double newNum;
        newNum = removeTrailingZeroes(num);
        newNum = truncateNumber(newNum);
        return newNum;
    }


    @FunctionalInterface
    public interface LengthJudger {
        int judgeLength(char c);
    }
}
