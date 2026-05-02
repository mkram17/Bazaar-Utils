package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * All player-facing communication goes through this class.
 *
 * <p>There are three tiers:</p>
 * <ul>
 *   <li>{@link #send} — user-facing prefixed chat messages</li>
 *   <li>{@link #sendError} — logs the error AND shows a red clickable chat notification</li>
 *   <li>{@link #debug} — always logs at DEBUG level; also shows in chat if the
 *       given {@link NotificationType} is enabled in DeveloperConfig</li>
 * </ul>
 *
 * For pure file logging with no player notification, use {@link BazaarLogger} directly.</p>
 */
public final class PlayerLogger {

    public static final String DISCORD_LINK = "https://discord.gg/xDKjvm5hQd";

    private PlayerLogger() {}

    /** Sends a plain white prefixed message to the player's chat. */
    public static void send(String message) {
        send(Component.literal(message).withStyle(ChatFormatting.WHITE));
    }

    /** Sends a component prefixed with [BazaarUtils] in gold to the player's chat. */
    public static void send(Component message) {
        MutableComponent prefixed = Component.literal("[Bazaar Utils]")
                .withStyle(ChatFormatting.GOLD)
                .append(" ")
                .append(message.copy());

        sendToPlayer(prefixed);
    }

    /**
     * Sends a prefixed message that the player can click to run a command.
     * The hover text shows "Run /command".
     */
    public static void sendWithCommand(MutableComponent message, String command) {
        message.withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/" + command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Run /" + command))));

        send(message);
    }

    /**
     * Logs the error to the log file AND, unless the player has disabled error
     * notifications in DeveloperConfig, shows a red clickable Discord link in chat.
     *
     * @param message   developer-readable description of what failed
     * @param throwable the cause, or {@code null} if there is none
     */
    public static void sendError(String message, Throwable throwable) {
        BazaarUtils.LOG.error(message, throwable);

        if (DeveloperConfig.DEVELOPER_MODE_DISABLE_ERROR_NOTIFICATIONS) return;

        try {
            MutableComponent errorText = Component.literal("[Bazaar Utils Error]: " + message + ". Click here for support.")
                    .withStyle(style -> {
                        try {
                            return style
                                    .withColor(ChatFormatting.RED)
                                    .withClickEvent(new ClickEvent.OpenUrl(new URI(DISCORD_LINK)))
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to join the Discord for support")));
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    });

            sendToPlayer(errorText);
        } catch (Exception e) {
            BazaarUtils.LOG.error("Failed to display error notification to player", e);
        }
    }

    /**
     * Always logs {@code message} at DEBUG level to the log file.
     * Additionally shows the message in the player's chat prefixed with the
     * calling class name, but only if {@code type.isEnabled()} is true.
     */
    public static void debug(String message, NotificationType type, BazaarLogger log) {
        log.debug(message);

        if (!type.isEnabled()) return;

        MutableComponent text = Component.literal("(%s)".formatted(type.name()))
                .withStyle(ChatFormatting.GOLD)
                .append(" ")
                .append(Component.literal(message).withStyle(ChatFormatting.DARK_GREEN));

        send(text);
    }

    public static void debug(String message, NotificationType type) {
        debug(message, type, BazaarUtils.LOG);
    }

    /**
     * Component-accepting overload of {@link #debug(String, NotificationType)}.
     */
    public static void debug(Component message, NotificationType type, BazaarLogger log) {
        log.debug(message.getString());

        if (!type.isEnabled()) return;

        send(message);
    }

    public static void debug(Component message, NotificationType type) {
        debug(message, type, BazaarUtils.LOG);
    }

    /** Sends a client-side command (without the leading slash). */
    public static void runCommand(String command) {
        Minecraft client = Minecraft.getInstance();

        if (client.player != null) {
            client.player.connection.sendCommand(command);
        }
    }

    private static void sendToPlayer(Component message) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.isSameThread()) {
            doSend(minecraft, message);
        } else {
            minecraft.execute(() -> doSend(minecraft, message));
        }
    }

    private static void doSend(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        } else {
            BazaarUtils.LOG.info("Player is null; retrying message in 100 ticks: " + message.getString());

            Util.tickExecuteLater(100, () -> sendToPlayer(message));
        }
    }
}