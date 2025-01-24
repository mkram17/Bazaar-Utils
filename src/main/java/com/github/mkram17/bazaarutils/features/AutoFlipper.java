package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.EventHandlers.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.EventHandlers.SignOpenEvent;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.configs.BUConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class AutoFlipper {
    public static double flipPrice;
    private static double orderPrice = -1;
    private static int orderVolumeFilled = -1;
    private static ItemData item;


    @SubscribeEvent
    public void onSignOpenedEvent(SignOpenEvent e) {
        if (!BUConfig.autoFlip)
            return;

        if (item != null)
            autoAddToSign(e);
    }

    @SubscribeEvent
    public void guiChestOpenedEvent(ChestLoadedEvent e) {
        if(BUConfig.autoFlip && BazaarUtils.gui.inFlipGui()) {
            item = getFlipItem(e);
            assert item != null : "Could not find flip item.";
            flipPrice = item.getFlipPrice();
        }
    }

    public static void autoAddToSign(SignOpenEvent e) {
            if(flipPrice != 0 && BazaarUtils.gui.wasLastChestFlip()) {
            GUIUtils.addToSign(Double.toString(Util.getPrettyNumber(flipPrice)), e.getGuiScreen());
            GUIUtils.closeGui();
            item.setPriceType(ItemData.priceTypes.INSTABUY);
        }
    }

    public static ItemData getFlipItem(ChestLoadedEvent e){

        for (ItemStack rawItem : e.getItemStacks()) {
            byte STRING_NBT_TAG = new NBTTagString().getId();
            NBTTagCompound tagCompound = rawItem.getTagCompound();

            if (tagCompound == null) continue;

            String displayName = Util.removeFormatting(tagCompound.getCompoundTag("display").getString("Name"));
            NBTTagList loreList = tagCompound.getCompoundTag("display").getTagList("Lore", STRING_NBT_TAG);

            if(displayName.contains("Flip Order")) {
                getItemInfo(loreList);

                //method updates item var
                if (matchFound()) {
                    return item;
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

    public static boolean matchFound() {
        item = ItemData.findItem(null, orderPrice, orderVolumeFilled, ItemData.priceTypes.INSTASELL);
        if (item != null) {
            if (item.getStatus() == ItemData.statuses.FILLED) {
                Util.notifyAll("Found match.", Util.notificationTypes.ITEMDATA);
                return true;
            }else {
                Util.notifyAll("found match, but isnt filled", Util.notificationTypes.ERROR);
                return true;
            }
        }
        Util.notifyAll("Couldnt find a match", Util.notificationTypes.ITEMDATA);
        return false;
    }


}
