package com.github.sirmegabite.bazaarutils.EventHandlers;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import com.github.sirmegabite.bazaarutils.Utils.Util;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import com.github.sirmegabite.bazaarutils.features.AutoFlipper;
import com.github.sirmegabite.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class EventHandler {
    public static List<ItemStack> bazaarStack = new ArrayList<>();
    public static String containerName = null;
    public static String itemLookingAt = null;
    //to run code once when gui opened
    public static boolean justOpened = false;

    @SubscribeEvent
    public void onBazaarChat(ClientChatReceivedEvent e) {
        if (!(e.message.getFormattedText().contains("[Bazaar]"))) return;

        String orderText;
        String item;
        int volume = -1;
        double price = -1;

        String unformattedOrderText = e.message.getFormattedText();
        orderText = Util.removeFormatting(unformattedOrderText);
        if (orderText.contains("Order Setup!")) {
//            item = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("for") - 1);
            item = orderText.substring(orderText.indexOf("x")+2, orderText.indexOf("f")-1);
            volume = Integer.parseInt(orderText.substring(orderText.indexOf("!") + 2, orderText.indexOf("x")).replace(",", ""));
            price = Double.parseDouble(orderText.substring(orderText.indexOf("for") + 4, orderText.indexOf("coins") - 1).replace(",", "")) / volume;
            Util.addWatchedItem(item, price, !orderText.contains("Buy"), volume);
            Util.notifyAll(item + " was added with a price of " + price);
        }
        if (orderText.contains("was filled!")) {
            volume = Integer.parseInt(orderText.substring(orderText.indexOf("for") + 4, orderText.indexOf("x")).replace(",", ""));
            item = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("was") - 1);
            ItemData.setItemFilled(item, volume);
            Util.notifyAll(item + " was filled");
        }
        if (orderText.contains("Claimed")){
            volume = Integer.parseInt(orderText.substring(orderText.indexOf("Claimed") + 8, orderText.indexOf("x")).replace(",", ""));
            item = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("worth") - 1);
            price = Double.parseDouble(orderText.substring(orderText.indexOf("worth") + 6, orderText.indexOf("coins") - 1).replace(",", ""))/volume;
            ItemData.removeItem(item, volume, price);
            Util.notifyAll(item + " was removed");
        }

    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        if(gui instanceof  AccessorGuiEditSign){

        }

//        if(BUConfig.pasting.isActive()){
//            //paste the clipboard
//            if(canPaste && gui instanceof AccessorGuiEditSign){
//                Util.notifyAll("Attempting to paste into sign");
//                Util.pasteIntoSign();
//                canPaste = false;
//            }
//        }

    }

    @SubscribeEvent
    public void onBazaarTick(TickEvent.ClientTickEvent e) {
        //if it isnt end phase, return
        if (e.phase != TickEvent.Phase.END) {
            return;
        }

        //if we arent a gui, return
        GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;
        if (!(currentScreen instanceof GuiChest)) {
            BazaarUtils.container = null;
            return;
        }

//        thanks to nea89o for this part
        GuiChest chestScreen = (GuiChest) currentScreen;
        ContainerChest guiContainer = (ContainerChest) chestScreen.inventorySlots;
        String containerName = guiContainer.getLowerChestInventory().getDisplayName().getFormattedText();

        bazaarStack = this.getBazaarStack(guiContainer);

        //update the global container variable

        if(BUConfig.autoFlip)
            AutoFlipper.autoAddToSign();

        BazaarUtils.container = guiContainer;
    }

    @SubscribeEvent
    public void guiChestOpenedEvent(GuiOpenEvent e) {

        if (!(e.gui instanceof GuiChest))
            return;
        AutoFlipper.allowPaste();
        justOpened = false;
        GuiChest chestScreen = (GuiChest) e.gui;
        ContainerChest guiContainer = (ContainerChest) chestScreen.inventorySlots;
        containerName = guiContainer.getLowerChestInventory().getDisplayName().getFormattedText();
        Util.notifyAll("Container Name: " + containerName);

    }

    //returns a list with all ItemStacks in gui
    public List<ItemStack> getBazaarStack(ContainerChest container) {
        List<ItemStack> bzStack = new ArrayList<>();
        for (int i = 0; i < container.getLowerChestInventory().getSizeInventory(); i++) {
            ItemStack stack = container.getLowerChestInventory().getStackInSlot(i);

            if (stack != null) {
//                Util.notifyAll("Slot " + i + ": " + stack, this.getClass());
                bzStack.add(stack);
            }
        }
        return bzStack;
    }

}