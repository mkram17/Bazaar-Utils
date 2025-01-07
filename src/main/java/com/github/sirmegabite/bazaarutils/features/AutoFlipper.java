package com.github.sirmegabite.bazaarutils.features;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.EventHandlers.EventHandler;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import com.github.sirmegabite.bazaarutils.Utils.Util;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import com.github.sirmegabite.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.concurrent.CompletableFuture;

import static com.github.sirmegabite.bazaarutils.EventHandlers.EventHandler.*;
import static com.github.sirmegabite.bazaarutils.configs.BUConfig.watchedItems;

public class AutoFlipper {
    public static double flipPrice;
    private static double orderPrice = -1;
    private static int orderVolumeFilled = -1;
    public enum guiTypes {CHEST, SIGN}
    public static guiTypes guiType;

    @SubscribeEvent
    public void guiChestOpenedEvent(GuiOpenEvent e) {
        if (!(e.gui instanceof GuiChest))
            return;
        guiType = guiTypes.CHEST;
        Util.notifyAll("In a chest", Util.notificationTypes.GUI);
    }

    @SubscribeEvent
    public void onSignOpenedEvent(GuiOpenEvent e){
        //is the gui a Accessorguieditsign even after the event? maybe not
        if (!(e.gui instanceof AccessorGuiEditSign) || !BUConfig.autoFlip)
            return;
        guiType = guiTypes.SIGN;

        Util.notifyAll("In a sign", Util.notificationTypes.GUI);
         autoAddToSign(e);
    }

    public static void autoAddToSign(GuiOpenEvent e) {
        if(flipPrice != -1 && inFlipGui()) {
            Util.addToSign(Double.toString(Util.getPrettyNumber(flipPrice)), e.gui);
            CompletableFuture.runAsync(AutoFlipper::closeGui);
        }
    }

    public static ItemData getFlipItem(){
        GuiChest chestScreen = (GuiChest) Minecraft.getMinecraft().currentScreen;
        ContainerChest guiContainer = (ContainerChest) chestScreen.inventorySlots;
        bazaarStack = EventHandler.getBazaarStack(guiContainer);

        for (ItemStack item : bazaarStack) {
            byte STRING_NBT_TAG = new NBTTagString().getId();
            NBTTagCompound tagCompound = item.getTagCompound();

            if (tagCompound == null) continue;

            String displayName = Util.removeFormatting(tagCompound.getCompoundTag("display").getString("Name"));
            NBTTagList loreList = tagCompound.getCompoundTag("display").getTagList("Lore", STRING_NBT_TAG);

            if(displayName.contains("Flip Order")) {
                getItemInfo(loreList);

                //tries to match order volume and price of item being flipped to the item in watchedItems
                //if they both return a match and find the same match
                if (matchFound()) {
                    return ItemData.findItem(null, orderPrice, orderVolumeFilled);
                }
            }
        }
        return null;
    }

    public static void getItemInfo(NBTTagList loreList) {
        //get order volume and price of item that is being flipped
        try {
            String orderPriceNBT = Util.removeFormatting(loreList.getStringTagAt(3));
            orderPrice = Double.parseDouble(orderPriceNBT.substring(orderPriceNBT.indexOf(":") + 2, orderPriceNBT.indexOf("coins") - 1));
            String orderVolumeNBT = Util.removeFormatting(loreList.getStringTagAt(1));
            orderVolumeFilled = Integer.parseInt(orderVolumeNBT.substring(0, orderVolumeNBT.indexOf("x")));

        } catch (Exception ex) {
            Util.notifyAll("Error while trying to find order price or volume ");
            ex.printStackTrace();
        }
    }
    public static boolean inFlipGui(){
        if(containerName == null || BazaarUtils.container == null)
            return false;
         return containerName.contains("Order options");
    }

    public static boolean matchFound() {
        ItemData item = ItemData.findItem(null, orderPrice, orderVolumeFilled);
        if (item != null) {
            if (item.getStatus() == ItemData.statuses.FILLED) {
                Util.notifyAll("Found a match: " + item.getName(), Util.notificationTypes.ITEMDATA);
                return true;
            }else {
                Util.notifyAll("found match, but isnt filled");
                return true;
            }
        }
        Util.notifyAll("Couldnt find a match");
        return false;
    }
    public static void closeGui(){
        try{
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Util.notifyAll("Closing gui", Util.notificationTypes.GUI);
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

}
