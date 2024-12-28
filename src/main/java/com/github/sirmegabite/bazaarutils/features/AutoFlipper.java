package com.github.sirmegabite.bazaarutils.features;

import com.github.sirmegabite.bazaarutils.Utils.BazaarData;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import com.github.sirmegabite.bazaarutils.Utils.Util;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import com.github.sirmegabite.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import static com.github.sirmegabite.bazaarutils.EventHandlers.EventHandler.*;

public class AutoFlipper {
    public static boolean canPaste = true;
    private static double orderPrice = -1;
    private static int orderVolume = -1;
    public static double flipPrice;

    public static void autoAddToSign(){
        Util.addToSign(Double.toString(getPrice()));
        Minecraft.getMinecraft().thePlayer.closeScreen();

    }

    public static double getPrice(){
        for (ItemStack item : bazaarStack) {
            byte STRING_NBT_TAG = new NBTTagString().getId();
            NBTTagCompound tagCompound = item.getTagCompound();

            if (tagCompound == null) continue;

            String displayName = Util.removeFormatting(tagCompound.getCompoundTag("display").getString("Name"));
            NBTTagList loreList = tagCompound.getCompoundTag("display").getTagList("Lore", STRING_NBT_TAG);
            getItemInfo(loreList);

            if (inFlipScreen(displayName)) {
                //tries to match order volume and price of item being flipped to the item in watchedItems
                    //if they both return a match and find the same match
                    if (matchFound()) {
                        int itemIndex = ItemData.findIndex(orderPrice, orderVolume);
                        String itemName = BUConfig.watchedItems.get(itemIndex).getName();
                        String priceType = BUConfig.watchedItems.get(itemIndex).getPriceType();
                        Util.notifyAll("Found a match: " + itemName);
                        String productID = BazaarData.findProductId(itemName);
                        double price = BazaarData.findItemPrice(productID, priceType);
                        if(priceType.equals("buyPrice")){
                            return price-.1;
                        }else{
                            return price+.1;
                        }
                    }

                }
        }
        return -1;
    }
    public static void getItemInfo(NBTTagList loreList){
        //get order volume and price of item that is being flipped
        try {
            String orderPriceNBT = Util.removeFormatting(loreList.getStringTagAt(3));
            orderPrice = Double.parseDouble(orderPriceNBT.substring(orderPriceNBT.indexOf(":") + 2, orderPriceNBT.indexOf("coins") - 1));
            String orderVolumeNBT = Util.removeFormatting(loreList.getStringTagAt(1));
            orderVolume = Integer.parseInt(orderVolumeNBT.substring(0, orderVolumeNBT.indexOf("x")));

        } catch (Exception ex) {
            Util.notifyAll("Error while trying to find order price or volume ");
            ex.printStackTrace();
        }
    }
    public static boolean inFlipScreen(String displayName){
        //makes sure that item with needed data is loaded in as well as in right screen
        if (containerName.contains("Order options") && displayName.equalsIgnoreCase("Flip Order"))
            return true;
        return false;

    }
    public static boolean matchFound(){
        if (ItemData.findIndex(orderPrice, orderVolume) != -1){
            int itemIndex = ItemData.findIndex(orderPrice, orderVolume);
                if(BUConfig.watchedItems.get(itemIndex).getStatus().equalsIgnoreCase("filled"))
                    return true;
        }
        return false;
    }
    public static void copyPriceToClipboard(String itemName, String priceType){
        //finds the price of opposite of order type, since it is being flipped
        if(canPaste) {
            if (priceType.equalsIgnoreCase("sellPrice"))
                Util.copyItem(itemName, "buyPrice");
            else
                Util.copyItem(itemName, "sellPrice");
            disablePaste();
        }
    }
    public static void allowPaste(){canPaste = true;}
    public static void disablePaste(){canPaste = false;}
}
