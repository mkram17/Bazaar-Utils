package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderPriceInfo;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
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
                .description(OptionDescription.of(Text.literal("Plays two short notification sounds when your order is filled.")))
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
                String totalPriceString = siblings.get(Util.findComponentIndex(siblings, "for")+1).getString().replace(",", "");
                totalPriceString = siblings.get(Util.findComponentIndex(siblings, "for")+1).getString().replace(",", "").substring(0, totalPriceString.indexOf(" "));
                price = Double.parseDouble(totalPriceString)/volume;
                OrderData itemToAdd;
                if (messageType == messageTypes.SELLORDER) {
                    //the price calculated before is ignoring tax, so must be added to find the actual price (which is used in tooltips etc.)
                    price /= ((100 - BUConfig.get().bzTax)/100);
                    itemToAdd = new OrderData(itemName, price*volume, OrderPriceInfo.priceTypes.INSTABUY, volume);
                } else
                    itemToAdd = new OrderData(itemName, price*volume, OrderPriceInfo.priceTypes.INSTASELL, volume);

                //for some reason 52800046 for 4 was on hypixel as 13200011.6 but calculates to 13200011.5. current theory is that buy price wasnt fully accurate, and it rounded up. also was .2 off on sell order for it. obviously problems with big prices
                Util.addWatchedItem(itemToAdd);
                Util.notifyAll(itemName + " was added with a price of " + itemToAdd.getPriceInfo().getPrice(), Util.notificationTypes.ITEMDATA);
            }

            if (messageType == messageTypes.FILLED) {
                String messageText = Util.removeFormatting(message.getString());
                
                try {
                    int forIndex = messageText.indexOf("for");
                    int xIndex = messageText.indexOf("x");
                    
                    if (forIndex == -1 || xIndex == -1 || forIndex >= xIndex) {
                        Util.notifyError("Invalid FILLED message format - missing 'for' or 'x': " + messageText, null);
                        return;
                    }
                    
                    String volumeStr = messageText.substring(forIndex + 4, xIndex).trim().replace(",", "");
                    if (volumeStr.isEmpty()) {
                        Util.notifyError("Empty volume string in FILLED message: " + messageText, null);
                        return;
                    }
                    
                    try {
                        volume = Integer.parseInt(volumeStr);
                    } catch (NumberFormatException e) {
                        Util.notifyError("Invalid volume format in FILLED message: " + volumeStr, e);
                        return;
                    }

                    int wasIndex = messageText.indexOf("was", xIndex);
                    if (wasIndex == -1 || wasIndex <= xIndex + 2) {
                        Util.notifyError("Invalid FILLED message format - missing or misplaced 'was': " + messageText, null);
                        return;
                    }

                    itemName = messageText.substring(xIndex + 2, wasIndex - 1).trim();
                    if (itemName.isEmpty()) {
                        Util.notifyError("Empty item name in FILLED message: " + messageText, null);
                        return;
                    }

                } catch (Exception e) {
                    Util.notifyError("Failed to parse FILLED message: " + messageText, e);
                    return;
                }

                if (!BUConfig.get().isOrderFilledSound())
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
                // Parse volume with validation
                String volumeStr = siblings.get(3).getString().replace(",", "").trim();
                if (volumeStr.isEmpty()) {
                    Util.notifyError("Empty volume string in claimed order", null);
                    return;
                }
                
                try {
                    volumeClaimed = Integer.parseInt(volumeStr);
                } catch (NumberFormatException e) {
                    Util.notifyError("Invalid volume format in claimed order: " + volumeStr, e);
                    return;
                }

                itemName = siblings.get(5).getString().trim();
                if (itemName.isEmpty()) {
                    Util.notifyError("Empty item name in claimed order", null);
                    return;
                }

                String priceString = siblings.get(7).getString();
                int coinsIndex = priceString.indexOf(" coins");
                if (coinsIndex == -1) {
                    Util.notifyError("Invalid price format - no 'coins' found in: " + priceString, null);
                    return;
                }

                String priceStr = priceString.substring(0, coinsIndex).replace(",", "").trim();
                if (priceStr.isEmpty()) {
                    Util.notifyError("Empty price string in claimed order", null);
                    return;
                }

                double totalPrice;
                try {
                    totalPrice = Double.parseDouble(priceStr);
                } catch (NumberFormatException e) {
                    Util.notifyError("Invalid price format in claimed order: " + priceStr, e);
                    return;
                }
                
                if (volumeClaimed == 0) {
                    Util.notifyError("Cannot divide by zero volume in claimed order", null);
                    return;
                }
                
                price = totalPrice / volumeClaimed;
                if (OrderData.getVariables(OrderData::getVolume).contains(volumeClaimed))
                    item = OrderData.findItem(itemName, price, volumeClaimed, OrderPriceInfo.priceTypes.INSTASELL);
                else
                    item = OrderData.findItem(itemName, price, null, OrderPriceInfo.priceTypes.INSTASELL);
            } else {
//                Util.notifyAll("claimed message, but not worth");
                //TODO figure out when there is a volume included in message
//                volumeClaimed = Integer.parseInt(siblings.get(5).getString().replace(",", ""));
                itemName = siblings.get(7).getString().trim();
                if (itemName.isEmpty()) {
                    Util.notifyError("Empty item name in SELLORDER claimed order", null);
                    return;
                }

                String priceString = siblings.get(9).getString();
                String priceStr = priceString.trim().replace(",", "");
                if (priceStr.isEmpty()) {
                    Util.notifyError("Empty price string in SELLORDER claimed order", null);
                    return;
                }
                
                try {
                    price = Double.parseDouble(priceStr);
                } catch (NumberFormatException e) {
                    Util.notifyError("Invalid price format in SELLORDER claimed order: " + priceStr, e);
                    return;
                }
                
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
                Util.notifyAll(
                        item.getName() + " has claimed " + item.getAmountClaimed() + " out of " + item.getVolume(),
                        Util.notificationTypes.ITEMDATA);
            }
        } catch (NumberFormatException e) {
            Util.notifyError("Failed to parse number in claimed order - Volume: " + volumeClaimed + ", Item: "
                    + itemName + ", Price: " + price, e);
        } catch (IllegalArgumentException e) {
            Util.notifyError("Invalid format in claimed order - " + e.getMessage() + ", Item: " + itemName, e);
        } catch (ArithmeticException e) {
            Util.notifyError("Math error in claimed order - " + e.getMessage() + ", Volume: " + volumeClaimed, e);
        } catch (Exception e) {
            Util.notifyError("Error in order claim text: " + siblings, e);
        }
    }

}
