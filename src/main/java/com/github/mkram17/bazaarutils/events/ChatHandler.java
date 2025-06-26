package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderPriceInfo;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.features.OrderPriceCapture;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ChatHandler implements BUListener{
    public enum messageTypes {BUYORDER, SELLORDER, FILLED, CLAIMED}

    public static Option<Boolean> createDisableOrderFilledSound() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Disable Order Filled Sound"))
                .description(OptionDescription.of(Text.literal("Plays three short notification sounds when your order is filled.")))
                .binding(false,
                        BUConfig.get()::isOrderFilledSound,
                        BUConfig.get()::setOrderFilledSound)
                .controller(BUConfig::createBooleanController)
                .build();
    }

    @Override
    public void subscribe() {
        registerBazaarChat();
    }

    public static void registerBazaarChat() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            ArrayList<Text> siblings = new ArrayList<>(message.getSiblings());
            if (!(message.getString().contains("[Bazaar]"))) return;
            if (!siblings.isEmpty()) {
                if (siblings.get(1).getString().contains("escrow")
                        || siblings.get(1).getString().contains("Submitting")
                        || siblings.get(1).getString().contains("Executing")
                        || siblings.get(1).getString().contains("Claiming") || (siblings.size() >= 5 && siblings.get(2).getString().contains("Cancelled")))
                    return;
            }

            String itemName;
            int volume;
            double price;
            OrderData item;
            messageTypes messageType = null;
            List<Text> messageSiblings = message.getSiblings();

            if (siblings.isEmpty() && message.getString().contains("was filled!")) messageType = messageTypes.FILLED;
            if (siblings.size() >= 3 && siblings.get(2).getString().contains("Buy Order Setup!"))
                messageType = messageTypes.BUYORDER;
            if (siblings.size() >= 3 && siblings.get(2).getString().contains("Sell Offer Setup!"))
                messageType = messageTypes.SELLORDER;
            if(siblings.size() >= 3 && siblings.get(2).getString().contains("Claimed")) messageType = messageTypes.CLAIMED;
//            if(siblings.size() >= 5 && siblings.get(2).getString().contains("Cancelled")) messageType = messageTypes.CANCELLED;

            if (messageType == messageTypes.BUYORDER || messageType == messageTypes.SELLORDER) {
                itemName = Util.removeFormatting(siblings.get(5).getString());
                volume = Integer.parseInt(siblings.get(3).getString().replace(",", ""));
                
                OrderPriceInfo.priceTypes orderType = messageType == messageTypes.SELLORDER ? 
                    OrderPriceInfo.priceTypes.INSTABUY : OrderPriceInfo.priceTypes.INSTASELL;
                    
                OrderPriceCapture.ConfirmationData cachedData = OrderPriceCapture.getCachedConfirmation(itemName, volume, orderType);
                
                OrderData itemToAdd;
                if (cachedData != null) {
                    itemToAdd = new OrderData(itemName, volume, new OrderPriceInfo(cachedData.pricePerUnit, orderType));
                    Util.notifyAll(itemName + " was added with EXACT price from confirmation: " + cachedData.pricePerUnit, Util.notificationTypes.ITEMDATA);
                } else {
                String totalPriceString = siblings.get(Util.findComponentIndex(siblings, "for")+1).getString().replace(",", "");
                totalPriceString = siblings.get(Util.findComponentIndex(siblings, "for")+1).getString().replace(",", "").substring(0, totalPriceString.indexOf(" "));
                price = Double.parseDouble(totalPriceString)/volume;
                if (messageType == messageTypes.SELLORDER) {
                    price /= ((100 - BUConfig.get().bzTax)/100);
                    itemToAdd = new OrderData(itemName, price*volume, OrderPriceInfo.priceTypes.INSTABUY, volume);
                        Util.notifyAll(itemName + " was added with ESTIMATED price (no confirmation data): " + price, Util.notificationTypes.ITEMDATA);
                    } else {
                    itemToAdd = new OrderData(itemName, price*volume, OrderPriceInfo.priceTypes.INSTASELL, volume);
                        Util.notifyAll(itemName + " was added with exact price from buy order: " + price, Util.notificationTypes.ITEMDATA);
                    }
                }

                Util.addWatchedItem(itemToAdd);
            }

            if (messageType == messageTypes.FILLED) {
                String messageText = Util.removeFormatting(message.getString());
                volume = Integer.parseInt(messageText.substring(messageText.indexOf("for") + 4, messageText.indexOf("x")).replace(",", ""));
                itemName = messageText.substring(messageText.indexOf("x") + 2, messageText.indexOf("was") - 1);

                if(!BUConfig.get().isOrderFilledSound())
                    SoundUtil.notifyMultipleTimes(2);

                if(messageText.contains("Sell Offer"))
                    item = OrderData.findItem(itemName, null, volume, OrderPriceInfo.priceTypes.INSTABUY);
                else
                    item = OrderData.findItem(itemName, null, volume, OrderPriceInfo.priceTypes.INSTASELL);
                if(item == null)
                    Util.notifyError("Could not find item to fill with info vol: "+ volume + " name: " + itemName, null);
                else {
                    item.setFilled();
                    Util.notifyAll(item.getName() + "[" + item.getIndex() + "] was filled", Util.notificationTypes.ITEMDATA);
                }
            }

            if (messageType == messageTypes.CLAIMED) {
                handleClaimed(siblings);
            }
        });
    }

    public static void handleClaimed(ArrayList<Text> siblings) {
        Integer volumeClaimed = null;
        Double price = null;
        String itemName = null;
        OrderData item;
        messageTypes orderType;

        if (siblings.get(6).getString().contains("worth")) orderType = messageTypes.BUYORDER;
        else orderType = messageTypes.SELLORDER;

        try {
            if (orderType == messageTypes.BUYORDER) {
                volumeClaimed = Integer.parseInt(siblings.get(3).getString().replace(",", ""));
                itemName = siblings.get(5).getString().trim();
                String priceString = siblings.get(7).getString();
                price = Double.parseDouble(priceString.substring(0, priceString.indexOf(" coins")).replace(",", ""))/volumeClaimed;
                if(OrderData.getVariables(OrderData::getVolume).contains(volumeClaimed))
                    item = OrderData.findItem(itemName, price, volumeClaimed, OrderPriceInfo.priceTypes.INSTASELL);
                else
                    item = OrderData.findItem(itemName, price, null, OrderPriceInfo.priceTypes.INSTASELL);
            } else {
//                Util.notifyAll("claimed message, but not worth");
                //TODO figure out when there is a volume included in message
//                volumeClaimed = Integer.parseInt(siblings.get(5).getString().replace(",", ""));
                itemName = siblings.get(7).getString().trim();
                String priceString = siblings.get(9).getString();
                price = Double.parseDouble(priceString.trim().replace(",", ""));
                item = OrderData.findItem(itemName, price, null, OrderPriceInfo.priceTypes.INSTABUY);
            }

//TODO fix finding if price is similar -- when it comes from chat message the price error can be greater than maximum rounding
            if (item == null) {
                Util.notifyAll("Could not find claimed item: " + itemName, Util.notificationTypes.ITEMDATA);
                return;
            }
            if (volumeClaimed != null && item.getVolume() == volumeClaimed) {
                Util.notifyAll(item.getGeneralInfo() + " was removed", Util.notificationTypes.ITEMDATA);
                item.removeFromWatchedItems();
            } else if(volumeClaimed != null){
                item.setAmountClaimed(item.getAmountClaimed() + volumeClaimed);
                Util.notifyAll(item.getName() + " has claimed " + item.getAmountClaimed() + " out of " + item.getVolume(), Util.notificationTypes.ITEMDATA);
            }
        } catch (Exception e) {
            Util.notifyError("Error in order claim text: " + siblings, e);
        }
    }

}
