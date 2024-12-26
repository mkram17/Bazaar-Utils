package com.github.sirmegabite.bazaarutils.Utils;

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

import static com.github.sirmegabite.bazaarutils.BazaarUtils.watchedItems;

public class Util {
    public static<T> void notifyAll(T message, Class<?> className) {
        String messageStr = message.toString();
        if(messageStr.toLowerCase().contains("exception"))
            messageStr = "§c" + messageStr;
        else
            messageStr = "§a" + messageStr;

        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(messageStr));
        LogManager.getLogger(className.getName()).info("[AutoBz] Message[" + message + "]");
//        FMLLog.info("[AutoBz] Message (FMLLog)[" + message + "]");
    }
    public static<T> void notifyConsole(T message, Class<?> className) {
        LogManager.getLogger(className.getName()).info("[AutoBz] Message[" + message + "]");
//        FMLLog.info("[AutoBz] Message (FMLLog)[" + message + "]");
    }

    public static void addWatchedItem(String itemName, Double price, boolean isSellOrder, int volume){
        itemName = itemName.toLowerCase();
        if(BazaarData.findProductId(itemName) != null) {
            if(isSellOrder)
                watchedItems.add(new ItemData(itemName, price, "buyPrice", volume));
            else
                watchedItems.add(new ItemData(itemName, price, "sellPrice", volume));
            notifyAll("Added item: § " + itemName, StarterCommands.class);
        } else {
            notifyAll("Could not add item: § " + itemName + " §a (is it spelled correctly?)", StarterCommands.class);
        }
    }

    public static void copyToClipboard(String clip, int watchedNum){
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
    public static void copyItem(String itemName, String priceType){
        String productID = BazaarData.findProductId(itemName);
        double price = BazaarData.findItemPrice(productID, priceType);
            try {
                if(priceType.equals("buyPrice")){
                    StringSelection stringSelection = new StringSelection(Double.toString(price-.1));
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
                }else{
                    StringSelection stringSelection = new StringSelection(Double.toString(price+.1));
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
                }
            } catch (Exception e){
                Util.notifyAll("Failed to copy updated buy price to clipboard", ItemData.class);
            }
        }

    public static String removeFormatting(String str){
        while(str.contains("§")){
            int index = str.indexOf("§");
            if (index + 1 < str.length()) {
                str = str.substring(0, index) + str.substring(index + 2);
            } else {
                str = str.substring(0, index);
            };
        }
        str = str.replace(",", "");
        return str;
    }

    public static boolean isKeyHeld(int key) {
        if (key == 0) {
            return false;
        } else if (key >= Keyboard.KEYBOARD_SIZE) {
            int pressedKey = (Keyboard.getEventKey() == 0) ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
            return Keyboard.getEventKeyState() && key == pressedKey;
        } else {
            return Keyboard.isKeyDown(key);
        }
    }

    public <T> void writeFile(T print) {
        //assumes that jsonString is already set with getBazaarJson future
//        System.out.println("Writing data to file...");
        try{
            Files.write(Paths.get("C:\\mc modding\\AutoBazaar\\src\\main\\resources\\Gui Data"), print.toString().getBytes());
            Util.notifyConsole("Data written to file successfully.", this.getClass());
//            Util.sendFirst(jsonString, 500, "Written to file: ");
        }catch (Exception e){
            System.out.println("Failed to write data to file");
            e.printStackTrace();
        }
    }

    public static void pasteIntoSign(){
        String str = getClipboardContents();
        GuiScreen thisGui = Minecraft.getMinecraft().currentScreen;
        if(!(thisGui instanceof AccessorGuiEditSign)) return;
        IChatComponent[] lines = ((AccessorGuiEditSign) thisGui).getTileSign().signText;
        int index = ((AccessorGuiEditSign) thisGui).getEditLine();
        String text = lines[index].getUnformattedText() + str;
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

    @FunctionalInterface
    public interface LengthJudger {
        int judgeLength(char c);
    }
}
