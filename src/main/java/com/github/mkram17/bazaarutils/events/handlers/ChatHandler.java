package com.github.mkram17.bazaarutils.events.handlers;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.events.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//TODO make finding order consistent. Some (eg handleClaimed) find the actual BazaarOrder object from userOrders, while others (eg handleFilled) just make a new OrderInfoContainer without finding the actual order
public class ChatHandler {
    public static Option<Boolean> createOrderFilledSoundOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Sound on Order Filled"))
                .description(OptionDescription.of(Component.literal("Plays two short notification sounds when your order is filled.")))
                .binding(true,
                        BUConfig.get()::isOrderFilledSound,
                        BUConfig.get()::setOrderFilledSound)
                .controller(BUConfigGui::createBooleanController)
                .build();
    }

    @RunOnInit
    public static void registerBazaarChat() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if(message.getString().contains("Error")) return;

            ArrayList<Component> siblings = new ArrayList<>(message.getSiblings());
            getMessageType(message, siblings).ifPresent(messageType -> {
                switch (messageType) {
                    case ORDER_CREATED -> handleOrderCreated(siblings);
                    case ORDER_FILLED -> handleFilled(message);
                    case ORDER_CLAIMED -> handleClaimed(siblings);
                    case INSTA_SELL -> handleInstaSell(siblings);
                    case INSTA_BUY -> handleInstaBuy(siblings);
                    case ORDER_FLIPPED -> handleFlip(siblings);
                    case ORDER_CANCELLED -> handleCancelled(siblings);
                }
            });
        });
    }

    private static Optional<BazaarChatEvent.BazaarEventTypes> getMessageType(Component message, ArrayList<Component> siblings) {
        if (siblings.isEmpty() && message.getString().contains("was filled!")) {
            return Optional.of(BazaarChatEvent.BazaarEventTypes.ORDER_FILLED);
        }

        if (siblings.size() > 3) {
            String identifier = siblings.get(2).getString();
            if (identifier.contains("Buy Order Setup!") || identifier.contains("Sell Offer Setup!")) {
                return Optional.of(BazaarChatEvent.BazaarEventTypes.ORDER_CREATED);
            }
            if (identifier.contains("Claimed")) return Optional.of(BazaarChatEvent.BazaarEventTypes.ORDER_CLAIMED);
            if (identifier.contains("Order Flipped!"))
                return Optional.of(BazaarChatEvent.BazaarEventTypes.ORDER_FLIPPED);

            identifier = siblings.get(1).getString();
            if (identifier.contains("Sold")) return Optional.of(BazaarChatEvent.BazaarEventTypes.INSTA_SELL);
            if (identifier.contains("Bought")) return Optional.of(BazaarChatEvent.BazaarEventTypes.INSTA_BUY);
            if (identifier.contains("Cancelled")) return Optional.of(BazaarChatEvent.BazaarEventTypes.ORDER_CANCELLED);
        }
        return Optional.empty();
    }

    private static Optional<OrderInfoContainer> parseOrderData(ArrayList<Component> siblings, int volumeIndex, int nameIndex, int priceIndex) {
        try {
            String volumeString = siblings.get(volumeIndex).getString().replace(",", "");
            int volume = Integer.parseInt(volumeString);

            String name = siblings.get(nameIndex).getString().trim();
            if (name.contains("x ")) { // For flipped orders
                name = name.substring(name.indexOf("x ") + 2);
            }

            String priceString = siblings.get(priceIndex).getString().replace(",", "");
            priceString = priceString.substring(0, priceString.indexOf(" "));
            double totalPrice = Double.parseDouble(priceString);

            double pricePerUnit = totalPrice / volume;

            return Optional.of(new OrderInfoContainer(name, volume, pricePerUnit, null, null));
        } catch (Exception e) {
            Util.notifyError("Failed to parse order data from chat: " + siblings.stream().map(Component::getString), e);
            return Optional.empty();
        }
    }

    private static void processOrderEvent(
            ArrayList<Component> siblings,
            BazaarChatEvent.BazaarEventTypes eventType,
            PriceInfoContainer.PriceType priceType,
            int volumeIndex,
            int nameIndex,
            int priceIndex
    ) {
        parseOrderData(siblings, volumeIndex, nameIndex, priceIndex).ifPresent(order -> {
            order.setPriceType(priceType);
            EVENT_BUS.post(new BazaarChatEvent<>(eventType, order));
        });
    }

    public static void handleFlip(ArrayList<Component> siblings) {
        int priceIndex = Util.componentIndexOf(siblings, "for") + 1;
        processOrderEvent(siblings, BazaarChatEvent.BazaarEventTypes.ORDER_FLIPPED, PriceInfoContainer.PriceType.INSTABUY, 3, 4, priceIndex);
    }

    public static void handleCancelled(ArrayList<Component> siblings) {
        int priceIndex = Util.componentIndexOf(siblings, "for") + 1;
        processOrderEvent(siblings, BazaarChatEvent.BazaarEventTypes.ORDER_CANCELLED, PriceInfoContainer.PriceType.INSTASELL, 2, 4, priceIndex);
    }

    public static void handleInstaSell(ArrayList<Component> siblings) {
        int priceIndex = Util.componentIndexOf(siblings, "for") + 1;
        processOrderEvent(siblings, BazaarChatEvent.BazaarEventTypes.INSTA_SELL, PriceInfoContainer.PriceType.INSTASELL, 2, 4, priceIndex);
    }

    public static void handleInstaBuy(ArrayList<Component> siblings) {
        processOrderEvent(siblings, BazaarChatEvent.BazaarEventTypes.INSTA_BUY, PriceInfoContainer.PriceType.INSTABUY, 2, 4, 6);
    }

    private static void handleFilled(Component message) {
        String messageString = Util.removeFormatting(message.getString());
        // Example: "Your Buy Order for 2,304x Mithril was filled!"
        String[] parts = messageString.split(" for |x | was filled!");
        if (parts.length < 3) {
            Util.notifyError("Invalid FILLED message format: " + messageString, new Throwable());
            return;
        }

        try {
            int volume = Integer.parseInt(parts[1].replace(",", ""));
            String itemName = parts[2].trim();

            PriceInfoContainer.PriceType priceType = messageString.contains("Sell Offer") ? PriceInfoContainer.PriceType.INSTABUY : PriceInfoContainer.PriceType.INSTASELL;
            OrderInfoContainer item = new OrderInfoContainer(itemName, volume, null, priceType, null);

            EVENT_BUS.post(new BazaarChatEvent<>(BazaarChatEvent.BazaarEventTypes.ORDER_FILLED, item));
        } catch (NumberFormatException e) {
            Util.notifyError("Invalid volume format in FILLED message: " + messageString, e);
        } catch (Exception e) {
            Util.notifyError("Failed to parse FILLED message: " + messageString, e);
        }
    }

    private static void handleOrderCreated(ArrayList<Component> siblings) {
        String itemName = Util.removeFormatting(getName(siblings));
        int volume = Integer.parseInt(siblings.get(3).getString().replace(",", ""));

        String totalPriceString = siblings.get(Util.componentLastIndexOf(siblings, "for") + 1).getString().replace(",", "");
        totalPriceString = totalPriceString.substring(0, totalPriceString.indexOf(" "));
        double price = Double.parseDouble(totalPriceString) / volume;

        boolean isSellOrder = siblings.get(2).getString().contains("Sell Offer Setup!");
        if (isSellOrder) {
            //the price calculated before is ignoring tax, so must be added to find the actual price (which is used in tooltips etc.)
            price /= ((100 - BUConfig.get().bzTax) / 100);
        }

        PriceInfoContainer.PriceType priceType = isSellOrder ? PriceInfoContainer.PriceType.INSTABUY : PriceInfoContainer.PriceType.INSTASELL;
        BazaarOrder orderToAdd = new BazaarOrder(itemName, volume, price, priceType);
        EVENT_BUS.post(new BazaarChatEvent<>(BazaarChatEvent.BazaarEventTypes.ORDER_CREATED, orderToAdd));
    }

    private static String getName(List<Component> siblings) {
        if (siblings.size() == 10) {
            return Util.removeFormatting(siblings.get(6).getString());
        } else {
            return Util.removeFormatting(siblings.get(5).getString());
        }
    }

    public static void handleClaimed(ArrayList<Component> siblings) {
        Optional<BazaarOrder> orderOptional;
        try {
            if (siblings.get(6).getString().contains("worth")) {
                orderOptional = getClaimedBuyOrder(siblings);
            } else {
                orderOptional = getClaimedSellOrder(siblings);
            }
        } catch (Exception e) {
            Util.notifyError("Error in order claim text: " + siblings, e);
            return;
        }
        if (orderOptional.isEmpty()) {
            Util.notifyError("Could not find claimed order in watched orders", new Throwable("Order Claim Error"));
            return;
        }
        BazaarOrder order = orderOptional.get();
        PlayerActionUtil.notifyAll(order.getName() + " has claimed " + order.getAmountClaimed() + " out of " + order.getVolume(), Util.notificationTypes.ORDERDATA);
        EVENT_BUS.post(new BazaarChatEvent<>(BazaarChatEvent.BazaarEventTypes.ORDER_CLAIMED, order));
    }

    private static Optional<BazaarOrder> getClaimedBuyOrder(ArrayList<Component> siblings) {
        // Parse volume with validation
        String volumeStr = siblings.get(3).getString().replace(",", "").trim();
        if (volumeStr.isEmpty()) {
            Util.notifyError("Empty volume string in claimed order", new Throwable());
            return Optional.empty();
        }

        int volumeClaimed = Integer.parseInt(volumeStr);

        String itemName = siblings.get(5).getString().trim();
        if (itemName.isEmpty()) {
            Util.notifyError("Empty item name in claimed order", new Throwable());
            return Optional.empty();
        }

        String priceString = siblings.get(7).getString();

        int coinsIndex = priceString.indexOf(" coins");
        if (coinsIndex == -1) {
            Util.notifyError("Invalid price format - no 'coins' found in: " + priceString, new Throwable());
            return Optional.empty();
        }

        String priceStr = priceString.substring(0, coinsIndex).replace(",", "").trim();
        if (priceStr.isEmpty()) {
            Util.notifyError("Empty price string in claimed order", new Throwable());
            return Optional.empty();
        }

        double totalPrice = Double.parseDouble(priceStr);

        if (volumeClaimed == 0) {
            Util.notifyError("Cannot divide by zero volume in claimed order", new Throwable());
            return Optional.empty();
        }

        double price = totalPrice / volumeClaimed;

        OrderInfoContainer item;
        if (OrderInfoContainer.getVariables(OrderInfoContainer::getVolume).contains(volumeClaimed)) {
            item = new OrderInfoContainer(itemName, volumeClaimed, price, PriceInfoContainer.PriceType.INSTASELL, null);
        } else {
            item = new OrderInfoContainer(itemName, null, price, PriceInfoContainer.PriceType.INSTASELL, null);
        }

        return getOrderInfo(item);
    }

    private static Optional<BazaarOrder> getClaimedSellOrder(ArrayList<Component> siblings) {
        // Sell order claimed messages sometimes include volume and sometimes don't

        Component volumeComponent = siblings.get(Util.componentIndexOf(siblings, "x") - 1);
        String volumeString = volumeComponent.getString();
        int volume = Integer.parseInt(volumeString.replace(",", "").trim());

        Component nameComponent = siblings.get(Util.componentIndexOf(siblings, "x") + 1);
        String name = nameComponent.getString().trim();

        Component priceComponent = siblings.get(Util.componentLastIndexOf(siblings, "at") + 1);
        String priceString = priceComponent.getString().replace(",", "").trim();
        double price = Double.parseDouble(priceString);

        OrderInfoContainer item = new OrderInfoContainer(name, volume, price, PriceInfoContainer.PriceType.INSTABUY, null);

        return getOrderInfo(item);
    }

    private static Optional<BazaarOrder> getOrderInfo(OrderInfoContainer item) {
        Optional<BazaarOrder> orderOptional = item.findOrderInList(BUConfig.get().userOrders);

        if (orderOptional.isEmpty()) {
            PlayerActionUtil.notifyAll("Could not find claimed item: " + item.getName(), Util.notificationTypes.ORDERDATA);
            return Optional.empty();
        }
        return orderOptional;
    }
}
