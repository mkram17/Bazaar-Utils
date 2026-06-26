package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//TODO change the message number instead of sending more
public class OutbidOrderHandler {

    @Getter @Setter
    private boolean autoOpenEnabled;
    @Getter @Setter
    private boolean notifyOutbid;
    @Getter @Setter
    private boolean notificationSound;

    public OutbidOrderHandler(boolean autoOpenEnabled, boolean notifyOutbid) {
        this.autoOpenEnabled = autoOpenEnabled;
        this.notifyOutbid = notifyOutbid;
        this.notificationSound = true;
    }

    public static MutableComponent getOutbidMessage(BazaarOrder order) {
        return createYourOrderForText(order)
                .append(Component.literal(" is now outdated.").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" Click to open bazaar orders").withStyle(ChatFormatting.GOLD));
    }
    public static MutableComponent getCompetitiveMessage(BazaarOrder order) {
        return createYourOrderForText(order)
                .append(Component.literal(" is no longer outdated.").withStyle(ChatFormatting.DARK_PURPLE));
    }
    public static MutableComponent getMatchedMessage(BazaarOrder order) {
        return createYourOrderForText(order)
                .append(Component.literal(" has been matched.").withStyle(ChatFormatting.YELLOW));
    }
    private static MutableComponent createYourOrderForText(BazaarOrder order){
        return Component.literal("Your " + order.getPriceType().getString().toLowerCase() + " order for ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(order.getVolume().toString() + " ").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal(order.getName()).withStyle(ChatFormatting.GOLD));
    }

    public static List<BazaarOrder> getOutbidOrders() {
        return BUConfig.get().userOrders.stream()
                .filter(order -> order.getOutbidStatus() == OrderInfoContainer.Statuses.OUTBID && order.getFillStatus() != OrderInfoContainer.Statuses.FILLED)
                .toList();
    }

    public Collection<Option<Boolean>> createOptions() {
        ArrayList<Option<Boolean>> options = new ArrayList<>();
        options.add(Option.<Boolean>createBuilder()
                .name(Component.literal("Open Bazaar on Outbid Orders"))
                .description(OptionDescription.of(Component.literal("Automatically open the bazaar after a delay when an order becomes outdated.")))
                .binding(false,
                        this::isAutoOpenEnabled,
                        this::setAutoOpenEnabled)
                .controller(BUConfigGui::createBooleanController)
                .build());
        options.add(Option.<Boolean>createBuilder()
                .name(Component.literal("Chat Notification on Outbid Orders"))
                .description(OptionDescription.of(Component.literal("Sends a message in chat when someone has undercut your order.")))
                .binding(true,
                        this::isNotifyOutbid,
                        this::setNotifyOutbid)
                .controller(BUConfigGui::createBooleanController)
                .build());
        options.add(Option.<Boolean>createBuilder()
                .name(Component.literal("Sound on Outbid Order"))
                .description(OptionDescription.of(Component.literal("Plays three short notification sounds when your order becomes outdated.")))
                .binding(true,
                        this::isNotificationSound,
                        this::setNotificationSound)
                .controller(BUConfigGui::createBooleanController)
                .build());
        return options;
    }
}
