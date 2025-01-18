package com.github.sirmegabite.bazaarutils.EventHandlers;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.Utils.GUIUtils;
import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import com.github.sirmegabite.bazaarutils.Utils.Util;
import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import com.github.sirmegabite.bazaarutils.features.AutoFlipper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EventHandler {

    @SubscribeEvent
    public void onBazaarChat(ClientChatReceivedEvent e){
        if (!(e.message.getFormattedText().contains("[Bazaar]"))) return;

        String orderText;
        String itemName;
        int volume;
        double price;
        ItemData item;

        String unformattedOrderText = e.message.getFormattedText();
        orderText = Util.removeFormatting(unformattedOrderText);
        Util.notifyAll(orderText, Util.notificationTypes.ITEMDATA);
              if (orderText.contains("Order Setup!") || orderText.contains("Offer Setup!")) {
                  itemName = orderText.substring(orderText.indexOf("x") + 2, orderText.lastIndexOf("f") - 1);
                  volume = Integer.parseInt(orderText.substring(orderText.indexOf("!") + 2, orderText.indexOf("x")).replace(",", ""));
                  price = Double.parseDouble(orderText.substring(orderText.indexOf("for") + 4, orderText.indexOf("coins") - 1).replace(",", "")) / volume;
                  if(orderText.contains("Offer Setup!"))
                    price /= (1-BUConfig.bzTax);
                  price = (Math.round(price*10))/10.0;
                  Util.addWatchedItem(itemName, price, !orderText.contains("Buy"), volume);
                  Util.notifyAll(itemName + " was added with a price of " + price, Util.notificationTypes.ITEMDATA);
              }
              if (orderText.contains("was filled!")) {
                  volume = Integer.parseInt(orderText.substring(orderText.indexOf("for") + 4, orderText.indexOf("x")).replace(",", ""));
                  itemName = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("was") - 1);
                  item = ItemData.findItem(itemName, null, volume);
                  assert item != null: "Could not find item";
                  ItemData.setItemFilled(item);
                  Util.notifyAll(item.getName() + "[" + item.getIndex() + "] was filled", Util.notificationTypes.ITEMDATA);
              }

        if (orderText.contains("Claimed")) {
            handleClaimed(orderText);
        }
    }
    public static void handleClaimed(String orderText){
        Integer volumeClaimed = null;
        Double price = null;
        String itemName = null;
        ItemData item;
        //there might be different claim messages
        try {
            if (orderText.contains("worth")) {
                volumeClaimed = Integer.parseInt(orderText.substring(orderText.indexOf("Claimed") + 8, orderText.indexOf("x")).replace(",", ""));
                itemName = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("worth") - 1);
                price = Double.parseDouble(orderText.substring(orderText.indexOf("worth") + 6, orderText.indexOf("coins") - 1).replace(",", "")) / volumeClaimed;
            }
            else if (orderText.contains("at")) {
                volumeClaimed = Integer.parseInt(orderText.substring(orderText.indexOf("selling") + 8, orderText.indexOf("x")).replace(",", ""));
                itemName = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("at") - 1);
//            price = Double.parseDouble(orderText.substring(orderText.indexOf("at") + 3, orderText.indexOf("each") - 1).replace(",", ""));
            }

            //wont work when the amount claimed is equal to the volume of another order
            if (ItemData.volumes.contains(volumeClaimed))
                item = ItemData.findItem(itemName, price, volumeClaimed);
            else
                item = ItemData.findItem(itemName, price, null);

            if (item == null) {
                Util.notifyAll("Could not find claimed item: " + itemName, Util.notificationTypes.ITEMDATA);
                return; // Or throw an exception depending on how you want to handle this error
            }
            if (item.getVolume() == volumeClaimed) {
                Util.notifyAll(item.getGeneralInfo() + " was removed", Util.notificationTypes.ITEMDATA);
                ItemData.removeItem(item);
            } else {
                item.setAmountClaimed(item.getAmountClaimed() + volumeClaimed);
                Util.notifyAll(item.getName() + " has claimed " + item.getAmountClaimed() + " out of " + item.getVolume(), Util.notificationTypes.ITEMDATA);
            }
        } catch (Exception ex) {
            Util.notifyAll("Unexpected error in order text: " + orderText, Util.notificationTypes.ERROR);
            ex.printStackTrace();
            System.out.println("error test");
        }
    }

    @SubscribeEvent
    public void guiChestOpenedEvent(GuiOpenEvent e) {
        BazaarUtils.gui = new GUIUtils(e);
    }

}