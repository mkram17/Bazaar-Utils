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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlayerActionUtil {
    // Accessed only on the client thread, including the scheduled retry.
    private static final Map<String, Component> pendingErrors = new LinkedHashMap<>();
    private static boolean errorRetryScheduled;

    public static void runCommand(String command){
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.connection.sendCommand(command);
        }
    }

    static void sendPlayerMessage(Component message){
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(message);
        } else {
            Util.logMessage("Could not send notification because player is null. Message: " + message);
            Util.tickExecuteLater(100, () -> sendPlayerMessage(message));
        }
    }

    /** Keeps the latest pending error per source, even when its retry count changes. */
    static void sendErrorMessage(String source, Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                pendingErrors.remove(source);
                client.player.sendSystemMessage(message);
                return;
            }

            pendingErrors.put(source, message.copy());
            if (!errorRetryScheduled) {
                errorRetryScheduled = true;
                Util.tickExecuteLater(100, PlayerActionUtil::sendPendingErrors);
            }
        });
    }

    private static void sendPendingErrors() {
        if (pendingErrors.isEmpty()) {
            errorRetryScheduled = false;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            Util.tickExecuteLater(100, PlayerActionUtil::sendPendingErrors);
            return;
        }

        List<Component> messages = List.copyOf(pendingErrors.values());
        pendingErrors.clear();
        errorRetryScheduled = false;
        for (Component message : messages) {
            client.player.sendSystemMessage(message);
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
