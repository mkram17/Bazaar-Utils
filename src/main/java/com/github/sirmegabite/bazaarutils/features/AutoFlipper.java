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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static com.github.sirmegabite.bazaarutils.EventHandlers.EventHandler.*;
import static com.github.sirmegabite.bazaarutils.configs.BUConfig.watchedItems;

public class AutoFlipper {
    public static boolean canPaste = true;
    public static double flipPrice;
    private static double orderPrice = -1;
    private static int orderVolumeFilled = -1;
    public enum guiTypes {CHEST, SIGN}
    public static guiTypes guiType;

    @SubscribeEvent
    public void guiChestOpenedEvent(GuiOpenEvent e) {
        if (!(e.gui instanceof GuiChest))
            return;
        AutoFlipper.allowPaste();
        guiType = guiTypes.CHEST;
        Util.notifyAll("I am in a chest");
    }

    @SubscribeEvent
    public void onSignOpenedEvent(GuiOpenEvent e){
        //is the gui a Accessorguieditsign even after the event? maybe not
        if (!(e.gui instanceof AccessorGuiEditSign) || !BUConfig.autoFlip)
            return;
        guiType = guiTypes.SIGN;

        Util.notifyAll("I am in a sign");
         autoAddToSign(e);
    }



    public static void autoAddToSign(GuiOpenEvent e) {
        if(flipPrice != -1)
         Util.addToSign(Double.toString(Util.getPrettyNumber(flipPrice)), e.gui);
//         Minecraft.getMinecraft().thePlayer.closeScreen();

    }

    public static double getPrice(){
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
                    int itemIndex = ItemData.findIndex(null, orderPrice, orderVolumeFilled);
                    ItemData flipItem = watchedItems.get(itemIndex);
                    Util.notifyAll("Found a match: " + flipItem.getName());
                    return flipItem.getFlipPrice();
                } else Util.notifyAll("Couldnt find a match");
            }
        }
        return -1;
    }


    public static boolean inSign(){
        return Minecraft.getMinecraft().currentScreen instanceof AccessorGuiEditSign;
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
        int itemIndex = ItemData.findIndex(null, orderPrice, orderVolumeFilled);
        if (itemIndex != -1)
            return watchedItems.get(itemIndex).getStatus() == ItemData.statuses.FILLED;
        return false;
    }
//
//    public static void copyPriceToClipboard(String itemName, String priceType) {
//        //finds the price of opposite of order type, since it is being flipped
//        if (canPaste) {
//            if (priceType.equalsIgnoreCase("sellPrice"))
//                Util.copyItem(itemName, ItemData.priceTypes.INSTABUY);
//            else
//                Util.copyItem(itemName, ItemData.priceTypes.INSTASELL);
//            disablePaste();
//        }
//    }

    public static void allowPaste() {
        canPaste = true;
    }

    public static void disablePaste() {
        canPaste = false;
    }
}
