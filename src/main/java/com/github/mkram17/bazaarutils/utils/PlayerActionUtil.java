package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class PlayerActionUtil {
    public static void runCommand(String command){
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.connection.sendCommand(command);
        }
    }

    static void sendPlayerMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            doSend(mc, message);
        } else {
            mc.execute(() -> doSend(mc, message));
        }
    }

    private static void doSend(Minecraft mc, Component message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        } else {
            Util.logMessage("Could not send notification because player is null. Message: " + message.getString());
            Util.tickExecuteLater(100, () -> sendPlayerMessage(message));
        }
    }

    public static void notifyAll(Component message) {
        MutableComponent messageText = Component.literal("[" + BazaarUtils.MOD_NAME + "] ").withStyle(ChatFormatting.GOLD);
        messageText.append(message.copy());

        sendPlayerMessage(messageText);
        Util.logMessage(message.getString());
    }

    public static void notifyAll(String message) {
        notifyAll(Component.literal(message).withStyle(ChatFormatting.WHITE));
    }

    //only used for developer messages and debugging. notifyAll(String/Text messsage) is used to send messages to the player
    public static void notifyAll(String message, NotificationType notificationType) {
        String callingName = Util.getCallingClassName();
        String simpleCallingName = callingName.substring(callingName.lastIndexOf(".") + 1);
        MutableComponent messageText = Component.literal("(" + simpleCallingName + ") ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.DARK_GREEN));

        if(notificationType.isEnabled())
            notifyAll(messageText);
    }

    public static void notifyChatCommand(MutableComponent message, String command){
        message.withStyle(style -> style
                                    .withClickEvent(new ClickEvent.RunCommand("/" + command))
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Run /" + command))));

        notifyAll(message);
    }
}
