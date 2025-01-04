package com.github.sirmegabite.bazaarutils.EventHandlers;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import com.github.sirmegabite.bazaarutils.Utils.Util;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import com.github.sirmegabite.bazaarutils.features.AutoFlipper;
import com.github.sirmegabite.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EventHandler {
    public static List<ItemStack> bazaarStack = new ArrayList<>();
    public static String containerName = null;
    //to run code once when gui opened

    public void onGuiLoaded(){
        CompletableFuture.runAsync(EventHandler::checkIfGuiLoaded).thenRun(() ->{
            bazaarStack = getBazaarStack(BazaarUtils.container);
            if(BUConfig.autoFlip && AutoFlipper.inFlipGui() && (AutoFlipper.guiType == AutoFlipper.guiTypes.CHEST)) {
                AutoFlipper.flipPrice = AutoFlipper.getPrice();
            }
        });
    }

    @SubscribeEvent
    public void onBazaarChat(ClientChatReceivedEvent e) throws Exception  {
        if (!(e.message.getFormattedText().contains("[Bazaar]"))) return;

        String orderText;
        String itemName;
        int volume = -1;
        double price = -1;
        ItemData item;

        String unformattedOrderText = e.message.getFormattedText();
        orderText = Util.removeFormatting(unformattedOrderText);
        if (orderText.contains("Order Setup!")) {
//            item = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("for") - 1);
            itemName = orderText.substring(orderText.indexOf("x")+2, orderText.indexOf("f")-1);
            volume = Integer.parseInt(orderText.substring(orderText.indexOf("!") + 2, orderText.indexOf("x")).replace(",", ""));
            price = Double.parseDouble(orderText.substring(orderText.indexOf("for") + 4, orderText.indexOf("coins") - 1).replace(",", "")) / volume;
            Util.addWatchedItem(itemName, price, !orderText.contains("Buy"), volume);
            Util.notifyAll(itemName + " was added with a price of " + price);
        }
        if (orderText.contains("was filled!")) {
            volume = Integer.parseInt(orderText.substring(orderText.indexOf("for") + 4, orderText.indexOf("x")).replace(",", ""));
            itemName = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("was") - 1);
            item = ItemData.getItem(ItemData.findIndex(itemName, null, volume));
            ItemData.setItemFilled(item);
            Util.notifyAll(itemName + " was filled");
        }
        if (orderText.contains("Claimed")){
            handleClaimed(orderText);
        }
    }
    public static void handleClaimed(String orderText){
        Util.notifyAll("Claim msg: " + orderText);
        Integer volumeClaimed = null;
        Double price = null;
        String itemName = null;
        int index;
        //there might be different claim messages
        if(orderText.contains("worth")) {
            volumeClaimed = Integer.parseInt(orderText.substring(orderText.indexOf("Claimed") + 8, orderText.indexOf("x")).replace(",", ""));
            itemName = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("worth") - 1);
            price = Double.parseDouble(orderText.substring(orderText.indexOf("worth") + 6, orderText.indexOf("coins") - 1).replace(",", ""))/volumeClaimed;
        }
        if(orderText.contains("at")){
            volumeClaimed = Integer.parseInt(orderText.substring(orderText.indexOf("selling") + 8, orderText.indexOf("x")).replace(",", ""));
            itemName = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("at") - 1);
            price = Double.parseDouble(orderText.substring(orderText.indexOf("Claimed") + 8, orderText.indexOf("coins") - 1).replace(",", ""))/volumeClaimed;
        }

        //wont work when the amount claimed is equal to the volume of another order
        if(ItemData.volumes.contains(volumeClaimed))
            index = ItemData.findIndex(itemName, price, volumeClaimed);
        else
            index = ItemData.findIndex(itemName, price, null);

        ItemData item = ItemData.getItem(index);
        if(item.getVolume() == volumeClaimed) {
            ItemData.removeItem(item);
            Util.notifyAll(itemName + " was removed");
        } else {
            item.setAmountClaimed(item.getAmountClaimed() + volumeClaimed);
            Util.notifyAll(itemName + " has claimed " + item.getAmountClaimed() + " out of " + item.getVolume());
        }
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

        GuiChest chestScreen = (GuiChest) currentScreen;
        ContainerChest guiContainer = (ContainerChest) chestScreen.inventorySlots;

        bazaarStack = this.getBazaarStack(guiContainer);

        BazaarUtils.container = guiContainer;
    }

    @SubscribeEvent
    public void guiChestOpenedEvent(GuiOpenEvent e) {
        if ((e.gui instanceof GuiChest)) {
            GuiChest chestScreen = (GuiChest) e.gui;
            ContainerChest guiContainer = (ContainerChest) chestScreen.inventorySlots;
            containerName = guiContainer.getLowerChestInventory().getDisplayName().getFormattedText();
            Util.notifyAll("Container Name: " + containerName);
            onGuiLoaded();
        }

    }


    public static void checkIfGuiLoaded() {
            while (true) {
                //sometimes gui has not loaded at this point causing errors but wont say anything
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                GuiChest chestScreen = (GuiChest) screen;
                ContainerChest container = (ContainerChest) chestScreen.inventorySlots;
                bazaarStack = getBazaarStack(container);

                int size = container.getLowerChestInventory().getSizeInventory();
                if (size == 0) {
                    continue;
                }

                ItemStack bottomRightItem = container.getLowerChestInventory().getStackInSlot(size - 1);

                if (bottomRightItem != null && !isItemLoading()) {
                    Util.notifyAll("Item detected in the bottom-right corner: " + bottomRightItem.getDisplayName());
                    break;
                }
            }
    }

    public static boolean isItemLoading(){
        for (ItemStack item : bazaarStack) {
            NBTTagCompound tagCompound = item.getTagCompound();

            if (tagCompound == null) continue;
            String displayName = Util.removeFormatting(tagCompound.getCompoundTag("display").getString("Name"));
            if(displayName.contains("Loading")) {
                Util.notifyAll("Loading item...");
                return true;
            }
        }
        return false;
    }

    //returns a list with all ItemStacks in gui
    public static List<ItemStack> getBazaarStack(ContainerChest container) {
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