package com.github.mkram17.bazaarutils.Events;

import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ChatHandler {
    private enum messageTypes {BUYORDER, SELLORDER, FILLED, CLAIMED, CANCELLED}

    public static void subscribe() {
        registerBazaarChat();
    }

    public static void registerBazaarChat() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!(message.getString().contains("[Bazaar]"))) return;
            if (!message.getSiblings().isEmpty()) {
                if (message.getSiblings().get(1).getString().contains("escrow")
                        || message.getSiblings().get(1).getString().contains("Submitting")
                        || message.getSiblings().get(1).getString().contains("Executing")
                        || message.getSiblings().get(1).getString().contains("Claiming")) return;
            }

            ArrayList<Text> siblings = new ArrayList<>(message.getSiblings());

            String itemName;
            int volume;
            double price;
            ItemData item;
            messageTypes messageType = null;
            List<Text> messageSiblings = message.getSiblings();

            if (siblings.isEmpty() && message.getString().contains("was filled!")) messageType = messageTypes.FILLED;
            if (siblings.size() >= 3 && siblings.get(2).getString().contains("Buy Order Setup!"))
                messageType = messageTypes.BUYORDER;
            if (siblings.size() >= 3 && siblings.get(2).getString().contains("Sell Offer Setup!"))
                messageType = messageTypes.SELLORDER;
            if(siblings.size() >= 3 && siblings.get(2).getString().contains("Claimed")) messageType = messageTypes.CLAIMED;
            if(siblings.size() >= 5 && siblings.get(2).getString().contains("Cancelled")) messageType = messageTypes.CANCELLED;

            if (messageType == messageTypes.BUYORDER || messageType == messageTypes.SELLORDER) {
                itemName = Util.removeFormatting(siblings.get(5).getString());
                volume = Integer.parseInt(siblings.get(3).getString().replace(",", ""));
                price = Util.getPrettyNumber(Double.parseDouble(siblings.get(7).getString().substring(0, siblings.get(7).getString().indexOf(" coin")).replace(",", "")));
                if (messageType == messageTypes.SELLORDER)
                    price /= (1 - BUConfig.get().bzTax);
                //for some reason 52800046 for 4 was on hypixel as 13200011.6 but calculates to 13200011.5. current theory is that buy price wasnt fully accurate, and it rounded up. also was .2 off on sell order for it. obviously problems with big prices
                Util.addWatchedItem(itemName, price, !(messageType == messageTypes.BUYORDER), volume);
                Util.notifyAll(itemName + " was added with a price of " + price, Util.notificationTypes.ITEMDATA);
            }

            if (messageType == messageTypes.FILLED) {
                String messageText = Util.removeFormatting(message.getString());
                volume = Integer.parseInt(messageText.substring(messageText.indexOf("for") + 4, messageText.indexOf("x")).replace(",", ""));
                itemName = messageText.substring(messageText.indexOf("x") + 2, messageText.indexOf("was") - 1);
                if(messageText.contains("Sell Offer"))
                    item = ItemData.findItem(itemName, null, volume, ItemData.priceTypes.INSTABUY);
                else
                    item = ItemData.findItem(itemName, null, volume, ItemData.priceTypes.INSTASELL);
                if(item == null)
                    Util.notifyAll("Could not find item to fill with info vol: "+ volume + " name: " + itemName, Util.notificationTypes.ERROR);
                else {
                    ItemData.setItemFilled(item);
                    Util.notifyAll(item.getName() + "[" + item.getIndex() + "] was filled", Util.notificationTypes.ITEMDATA);
                }
            }

            if (messageType == messageTypes.CLAIMED) {
                handleClaimed(siblings);
            }if (messageType == messageTypes.CANCELLED) {
                handleCancelled(siblings);
            }
        });
    }

    public static void handleCancelled(ArrayList<Text> siblings) {
        double totalPrice;
        String priceString = siblings.get(4).getString();
        totalPrice = Double.parseDouble(priceString.substring(0, priceString.indexOf(" coins")).replace(",", ""));
        ItemData item = ItemData.findItemTotalPrice(totalPrice);
        if(item != null){
            item.remove();
        } else{
            Util.notifyAll("Error finding cancelled item.", Util.notificationTypes.ERROR);

        }
    }

    public static void handleClaimed(ArrayList<Text> siblings) {
        Integer volumeClaimed = null;
        Double price = null;
        String itemName = null;
        ItemData item;
        messageTypes orderType;

        if (siblings.get(6).getString().contains("worth")) orderType = messageTypes.BUYORDER;
        else orderType = messageTypes.SELLORDER;

        try {
            if (orderType == messageTypes.BUYORDER) {
                volumeClaimed = Integer.parseInt(siblings.get(3).getString().replace(",", ""));
                itemName = siblings.get(5).getString().trim();
                String priceString = siblings.get(7).getString();
                price = Double.parseDouble(priceString.substring(0, priceString.indexOf(" coins")).replace(",", ""))/volumeClaimed;
                if(ItemData.getVariables(ItemData::getVolume).contains(volumeClaimed))
                    item = ItemData.findItem(itemName, price, volumeClaimed, ItemData.priceTypes.INSTASELL);
                else
                    item = ItemData.findItem(itemName, price, null, ItemData.priceTypes.INSTASELL);
            } else {
//                Util.notifyAll("claimed message, but not worth");
                volumeClaimed = Integer.parseInt(siblings.get(5).getString().replace(",", ""));
                itemName = siblings.get(7).getString().trim();
                String priceString = siblings.get(7).getString();
                price = Double.parseDouble(priceString.substring(0, priceString.indexOf(" coins")).replace(",", ""))/volumeClaimed;
                item = ItemData.findItem(itemName, price, volumeClaimed, ItemData.priceTypes.INSTABUY);
            }



            if (item == null) {
                Util.notifyAll("Could not find claimed item: " + itemName, Util.notificationTypes.ITEMDATA);
                return;
            }
            if (item.getVolume() == volumeClaimed) {
                Util.notifyAll(item.getGeneralInfo() + " was removed", Util.notificationTypes.ITEMDATA);
                ItemData.removeItem(item);
            } else {
                item.setAmountClaimed(item.getAmountClaimed() + volumeClaimed);
                Util.notifyAll(item.getName() + " has claimed " + item.getAmountClaimed() + " out of " + item.getVolume(), Util.notificationTypes.ITEMDATA);
            }
        } catch (Exception ex) {
            Util.notifyAll("Error in order text: " + siblings, Util.notificationTypes.ERROR);
            ex.printStackTrace();
        }
    }

}
