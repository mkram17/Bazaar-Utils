package com.github.mkram17.bazaarutils.Events;

import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.List;

public class EventHandler {
    public static void subscribe(){
        registerBazaarChat();
    }

    private enum messageTypes {BUYORDER, SELLORDER}

    public static void registerBazaarChat(){
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!(message.getString().contains("[Bazaar]"))) return;
            if(message.getSiblings().size() < 1) return;
            if(message.getSiblings().get(1).getString().contains("escrow")) return;
            if(message.getSiblings().get(1).getString().contains("Submitting")) return;


            Text[] siblings = message.getSiblings().toArray(new Text[0]);


            String itemName;
            int volume;
            double price;
            ItemData item;
            messageTypes messageType = null;
            List<Text> orderText = message.getSiblings();
//            Util.notifyAll(orderText, Util.notificationTypes.ITEMDATA);

            if(siblings.length >=3 && siblings[2].getString().contains("Buy Order Setup!")) messageType = messageTypes.BUYORDER;
            if(siblings.length >=3 && siblings[2].getString().contains("Sell Offer Setup!")) messageType = messageTypes.SELLORDER;

            if(messageType == messageTypes.BUYORDER || messageType == messageTypes.SELLORDER){
                itemName = Util.removeFormatting(siblings[5].getString());
                volume = Integer.parseInt(siblings[3].getString());
                price = Double.parseDouble(siblings[7].getString().substring(0, siblings[7].getString().indexOf(" coin"))) / volume;
                if(messageType == messageTypes.SELLORDER)
                    price /= (1- BUConfig.bzTax);
                price = (Math.round(price*10))/10.0;
                Util.addWatchedItem(itemName, price, !(messageType == messageTypes.BUYORDER), volume);
                Util.notifyAll(itemName + " was added with a price of " + price, Util.notificationTypes.ITEMDATA);
            }

//            if (orderText.contains("was filled!")) {
//                volume = Integer.parseInt(orderText.substring(orderText.indexOf("for") + 4, orderText.indexOf("x")).replace(",", ""));
//                itemName = orderText.substring(orderText.indexOf("x") + 2, orderText.indexOf("was") - 1);
//                item = ItemData.findItem(itemName, null, volume, null);
//                assert item != null: "Could not find item";
//                ItemData.setItemFilled(item);
//                Util.notifyAll(item.getName() + "[" + item.getIndex() + "] was filled", Util.notificationTypes.ITEMDATA);
//            }
//
//            if (orderText.contains("Claimed")) {
//                handleClaimed(orderText);
//            }
        });

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
            if (ItemData.volumeList.contains(volumeClaimed))
                item = ItemData.findItem(itemName, price, volumeClaimed, null);
            else
                item = ItemData.findItem(itemName, price, null, null);

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
}
