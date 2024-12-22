package com.github.sirmegabite.bazaarutils.EventHandlers;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import com.github.sirmegabite.bazaarutils.Utils.Util;
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

import static com.github.sirmegabite.bazaarutils.BazaarUtils.*;

public class EventHandlers {
    private static int tickCount = 0;
    List<ItemStack> bazaarStack = new ArrayList<>();

    public static String getContainerName() {
        return containerName;
    }

    private static String containerName = null;

    public static String getItemLookingAt() {
        return itemLookingAt;
    }

    public static String itemLookingAt = null;
    //to run code once when gui opened
    private static boolean justOpened = false;
    private static boolean canPaste = true;

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
            Util.notifyAll(item + " was added with a price of " + price, this.getClass());
        }
        if (orderText.contains("was filled!")) {
            volume = Integer.parseInt(orderText.substring(orderText.indexOf("for") + 4, orderText.indexOf("x")).replace(",", ""));
            item = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("was") - 1);
            ItemData.setItemFilled(item, volume);
            Util.notifyAll(item + " was filled", this.getClass());
        }
        if (orderText.contains("Claimed")){
            volume = Integer.parseInt(orderText.substring(orderText.indexOf("Claimed") + 8, orderText.indexOf("x")).replace(",", ""));
            item = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("worth") - 1);
            price = Double.parseDouble(orderText.substring(orderText.indexOf("worth") + 6, orderText.indexOf("coins") - 1).replace(",", ""))/volume;
            ItemData.removeItem(item, volume, price);
            Util.notifyAll(item + " was removed", this.getClass());
        }

    }
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        try {
            ctrlDown = Util.isKeyHeld(Keyboard.KEY_LCONTROL);
            vDown = Util.isKeyHeld(Keyboard.KEY_V);
        } catch (Exception ex) {
//            Util.notifyAll("Error due to pressed keys", this.getClass());
            ex.printStackTrace();
        }

        if(ctrlDown && vDown){
            //paste the clipboard
            if(canPaste && gui instanceof AccessorGuiEditSign){
                Util.notifyAll("Attempting to paste into sign", this.getClass());
                Util.pasteIntoSign();
                canPaste = false;
            }
        }

    }

    @SubscribeEvent
    public void onBazaarTick(TickEvent.ClientTickEvent e) {
        //if it isnt end phase, return
        if (e.phase == TickEvent.Phase.END) {
            tickCount++;
        } else return;

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

        //only runs once when the gui is opened
//        if(AutoBazaar.container == null || containerName.contains("Bazaar") && !containerName.equals(AutoBazaar.container.getLowerChestInventory().getDisplayName().getFormattedText())) {
        //nea89o

        for (ItemStack item : bazaarStack) {
            byte STRING_NBT_TAG = new NBTTagString().getId();
            NBTTagCompound tagCompound = item.getTagCompound();

            if (tagCompound == null) continue;

            String displayName = Util.removeFormatting(tagCompound.getCompoundTag("display").getString("Name"));
            NBTTagList loreList = tagCompound.getCompoundTag("display").getTagList("Lore", STRING_NBT_TAG);

            //if in flip screen and it has been loaded in
            if (containerName.contains("Order options") && displayName.contains("Flip Order")) {
                double orderPrice = -1;
                int orderVolume = -1;

                //get order volume and price of item that is being flipped
                try {
                    String orderPriceNBT = Util.removeFormatting(loreList.getStringTagAt(3));
                    orderPrice = Double.parseDouble(orderPriceNBT.substring(orderPriceNBT.indexOf(":") + 2, orderPriceNBT.indexOf("coins") - 1));
                    String orderVolumeNBT = Util.removeFormatting(loreList.getStringTagAt(1));
                    orderVolume = Integer.parseInt(orderVolumeNBT.substring(0, orderVolumeNBT.indexOf("x")));

                } catch (Exception ex) {
                    Util.notifyAll("Error while trying to find order price or volume ", this.getClass());
                    ex.printStackTrace();
                }
                //tries to match order volume and price of item being flipped to the item in watchedItems
                if (displayName.equalsIgnoreCase("Flip Order")) {
                    //if they both return a match and find the same match
                    if (ItemData.findIndex(orderPrice, orderVolume) != -1) {
                        int itemIndex = ItemData.findIndex(orderPrice, orderVolume);
                        String itemName = watchedItems.get(itemIndex).getName();
                        String priceType = watchedItems.get(itemIndex).getPriceType();
                        if (watchedItems.get(itemIndex).getStatus().equalsIgnoreCase("filled")) {
                            if (!justOpened) {
                                Util.notifyAll("Found a match: " + itemName, this.getClass());
                                justOpened = true;
                                //finds the price of opposite of order type, since it is being flipped
                                if (priceType.equalsIgnoreCase("sellPrice"))
                                    Util.copyItem(itemName, "buyPrice");
                                else
                                    Util.copyItem(itemName, "sellPrice");
                            }

                        }
                    }
                }
            }

        }
        BazaarUtils.container = guiContainer;
    }

    @SubscribeEvent
    public void guiOpenedEvent(GuiOpenEvent e) {
        canPaste = true;
        if (!(e.gui instanceof GuiChest))
            return;
        justOpened = false;
        GuiChest chestScreen = (GuiChest) e.gui;
        ContainerChest guiContainer = (ContainerChest) chestScreen.inventorySlots;
        containerName = guiContainer.getLowerChestInventory().getDisplayName().getFormattedText();
        Util.notifyAll("Container Name: " + containerName, this.getClass());

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