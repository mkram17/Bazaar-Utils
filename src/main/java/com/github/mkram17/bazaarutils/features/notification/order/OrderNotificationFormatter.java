package com.github.mkram17.bazaarutils.features.notification.order;

import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.features.notification.DiscordPayload;
import com.github.mkram17.bazaarutils.features.notification.NotificationPayload;
import com.github.mkram17.bazaarutils.features.notification.NotificationPayload.Content;
import com.github.mkram17.bazaarutils.features.notification.OrderNotificationKind;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Builds all channel representations ({@link Content}) for order notification events.
 */
final class OrderNotificationFormatter {

    private static final int RED = 0xFF5555;
    private static final int GREEN = 0x55FF55;
    private static final int YELLOW = 0xFFFF55;
    private static final int AQUA = 0x55FFFF;
    private static final int GRAY = 0xAAAAAA;

    private OrderNotificationFormatter() {}

    static String nameOf(Order order) {
        return OrderInfo.of(order).map(OrderInfo::getName).orElse(order.productId());
    }

    static NotificationPayload.NotificationSubject subjectOf(Order order) {
        return new NotificationPayload.NotificationSubject(
                nameOf(order),
                order.side().name() + ":" + order.productId() ,
                order.id().toString()
        );
    }

    private static String fmt(double price) {
        return String.format("%,.1f", price);
    }

    private static ChatFormatting sideColor(Order order) {
        return order.isBuyOrder() ? ChatFormatting.GREEN : ChatFormatting.GOLD;
    }

    private static String sideLabel(Order order) {
        return order.isBuyOrder() ? "Buy Order" : "Sell Offer";
    }

    private static String possessive(Order order) {
        return order.coopOrder() ? "Their" : "Your";
    }

    private static String fillFraction(Order order) {
        int pct = order.originalAmount() > 0
                ? (order.filledAmount() * 100 / order.originalAmount()) : 0;

        return String.format("%,d/%,d (%d%%)", order.filledAmount(), order.originalAmount(), pct);
    }

    private static MutableComponent screenSubtitle(Order order) {
        return Component.literal(nameOf(order)).withStyle(sideColor(order))
                .append(Component.literal(" @ " + fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD));
    }

    private static Content.ScreenTitle screenTitle(String label, ChatFormatting color, Order order) {
        return new Content.ScreenTitle(
                Component.literal(label).withStyle(color),
                screenSubtitle(order));
    }

    private static DiscordPayload.Embed.Builder baseEmbed(String title, int color, Order order, MutableComponent chat) {
        return DiscordPayload.Embed.builder(title, color)
                .description(chat.getString())
                .wideField("Id", order.id().toString())
                .field("Product", nameOf(order))
                .field("Price", fmt(order.pricePerItem()) + "/unit")
                .field("Type", sideLabel(order))
                .field("Amount", String.format("%,d×", order.originalAmount()));
    }

    private static void addCoopField(DiscordPayload.Embed.Builder builder, Order order) {
        if (order.coopOrder()) builder.field("Co-op", "⚠ Yes");
    }

    static Content competitive(Order order) {
        MutableComponent chat = Component.literal("Competitive! ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                .append(Component.literal(" is now the top offer for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%,d× ", order.originalAmount())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD));

        var embed = baseEmbed("🏆 Competitive", GREEN, order, chat);
        addCoopField(embed, order);

        return Content.of(chat, embed.build(),
                screenTitle("Competitive", ChatFormatting.GREEN, order));
    }

    static Content matched(Order order) {
        MutableComponent chat = Component.literal("Matched! ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                .append(Component.literal(" now matches the best price for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%,d× ", order.originalAmount())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD));

        var embed = baseEmbed("⚡ Matched", YELLOW, order, chat);
        addCoopField(embed, order);

        return Content.of(chat, embed.build(),
                screenTitle("Matched", ChatFormatting.YELLOW, order));
    }

    /** Click hint ("[Click to search]") is appended by the CHAT dispatcher — not here. */
    static Content outbid(Order order) {
        MutableComponent chat = Component.literal("Outbid! ").withStyle(ChatFormatting.RED)
                .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                .append(Component.literal(" is no longer the top offer for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%,d× ", order.originalAmount())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD));

        var embed = baseEmbed("⚠️ Outbid", RED, order, chat);
        addCoopField(embed, order);

        return Content.of(chat, embed.build(),
                screenTitle("Outbid", ChatFormatting.RED, order));
    }

    /**
     * Placed — confirmed via transaction menu, or synthesized by screen reconciliation.
     * Co-op badge applies when the screen synthesized another member's order.
     */
    static Content placed(Order order) {
        MutableComponent chat = Component.literal("Placed! ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                .append(Component.literal(" is now live — ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%,d× ", order.originalAmount())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD));

        var embed = baseEmbed("📋 Placed", AQUA, order, chat);
        addCoopField(embed, order);

        return Content.of(chat, embed.build(),
                screenTitle("Placed", ChatFormatting.AQUA, order));
    }

    static Content partiallyFilled(UserOrderEvent.PartiallyFilled event) {
        Order order = event.getOrder();
        int delta = event.getFilledDelta();
        int pct = order.originalAmount() > 0
                ? (order.filledAmount() * 100 / order.originalAmount()) : 0;

        MutableComponent chat = Component.literal(pct + "%! ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                .append(Component.literal(" filled ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("+%,d× ", delta)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.format(" (%,d/%,d)", order.filledAmount(), order.originalAmount()))
                        .withStyle(ChatFormatting.DARK_GRAY));

        var embed = baseEmbed("🔄 Partially Filled", YELLOW, order, chat);
        embed.field("Filled Now", String.format("+%,d×", delta));
        embed.field("Fill", fillFraction(order));
        addCoopField(embed, order);

        return Content.of(chat, embed.build(),
                new Content.ScreenTitle(
                        Component.literal(pct + "% filled").withStyle(ChatFormatting.YELLOW),
                        screenSubtitle(order)));
    }

    /** Filled — market activity; may fire for a co-op member's order. */
    static Content filled(Order order) {
        MutableComponent chat = Component.literal("Filled! ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                .append(Component.literal(" was completely filled — ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%,d× ", order.originalAmount())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD));

        var embed = baseEmbed("✅ Filled", GREEN, order, chat);
        addCoopField(embed, order);

        return Content.of(chat, embed.build(),
                screenTitle("Filled", ChatFormatting.GREEN, order));
    }

    static Content claimed(UserOrderEvent.Claimed event) {
        Order order = event.getOrder();
        int delta = event.getClaimedDelta();
        boolean terminal = order.status() instanceof OrderStatus.Claimed;

        MutableComponent chat;
        if (terminal) {
            chat = Component.literal("Claimed! ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                    .append(Component.literal(" was claimed in full — ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%,d× ", order.filledAmount())).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD));
        } else {
            chat = Component.literal("Claimed ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%,d× ", delta)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("from ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(String.format(" (%,d/%,d claimed)", order.claimedAmount(), order.filledAmount()))
                            .withStyle(ChatFormatting.DARK_GRAY));
        }

        var embed = baseEmbed("📦 Claimed", GRAY, order, chat);
        if (terminal) {
            embed.wideField("Claimed", String.format("%,d×", order.filledAmount()));
        } else {
            embed.field("This Claim", String.format("%,d×", delta));
            embed.wideField("Total Claimed", String.format("%,d / %,d×", order.claimedAmount(), order.filledAmount()));
        }
        addCoopField(embed, order);

        Content.ScreenTitle screen = terminal
                ? screenTitle("Claimed", ChatFormatting.WHITE, order)
                : new Content.ScreenTitle(
                Component.literal(String.format("Claimed %,d×", delta)).withStyle(ChatFormatting.WHITE),
                screenSubtitle(order));

        return Content.of(chat,
                embed.build(),
                screen);
    }

    static Content cancelled(Order order) {
        MutableComponent chat = Component.literal("Cancelled! ").withStyle(ChatFormatting.RED)
                .append(Component.literal(possessive(order) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sideLabel(order)).withStyle(sideColor(order)))
                .append(Component.literal(" was cancelled — ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%,d× ", order.unfilledAmount())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nameOf(order)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(order.pricePerItem())).withStyle(ChatFormatting.GOLD));

        var embed = baseEmbed("❌ Cancelled", RED, order, chat);
        embed.wideField("Returned", String.format("%,d× unfilled", order.unfilledAmount()));
        addCoopField(embed, order);

        return Content.of(chat, embed.build(),
                screenTitle("Cancelled", ChatFormatting.RED, order));
    }

    static Content flipped(UserOrderEvent.Flipped event) {
        Order buy = event.getOrder();
        Order sell = event.getNewOrder();

        MutableComponent chat = Component.literal("Flipped! ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(possessive(buy) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Buy Order").withStyle(sideColor(buy)))
                .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%,d× ", buy.originalAmount())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nameOf(buy)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" — buy @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(buy.pricePerItem())).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" → sell @ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fmt(sell.pricePerItem())).withStyle(ChatFormatting.YELLOW));

        // Custom embed — buy/sell prices replace the generic Price field, but Id/Co-op still
        // follow the same convention as every other kind's embed.
        var embed = DiscordPayload.Embed.builder("🔃 Flipped", GREEN)
                .description(chat.getString())
                .wideField("Id", buy.id().toString())
                .field("Product",    nameOf(buy))
                .field("Buy Price",  fmt(buy.pricePerItem())  + "/unit")
                .field("Sell Price", fmt(sell.pricePerItem()) + "/unit")
                .wideField("Amount", String.format("%,d×", sell.originalAmount()));
        addCoopField(embed, buy);

        return Content.of(chat, embed.build(),
                new Content.ScreenTitle(
                        Component.literal("Flipped →").withStyle(ChatFormatting.GREEN),
                        screenSubtitle(sell)));
    }

    static Content batched(int distinctCount, NotificationPayload<?> latest, OrderNotificationKind kind) {
        NotificationPayload.NotificationSubject subject = latest.subject();

        MutableComponent prefix = Component.literal(distinctCount + " orders for ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(subject.label()).withStyle(ChatFormatting.GOLD));

        record Fmt(String suffix, ChatFormatting color, String embedTitle, int embedColor, String shortVerb) {}

        var fmt = switch (kind) {
            case COMPETITIVE      -> new Fmt(" are now top offers",       ChatFormatting.GREEN,  "🏆 Competitive",      GREEN,  "competitive");
            case MATCHED          -> new Fmt(" now match the best price", ChatFormatting.YELLOW, "⚡ Matched",          YELLOW, "matched");
            case OUTBID           -> new Fmt(" are no longer top offers", ChatFormatting.RED,    "⚠️ Outbid",           RED,    "outbid");
            case PLACED           -> new Fmt(" are now live",             ChatFormatting.AQUA,   "📋 Placed",           AQUA,   "placed");
            case PARTIALLY_FILLED -> new Fmt(" are partially filled",     ChatFormatting.YELLOW, "🔄 Partially Filled", YELLOW, "partial fill");
            case FILLED           -> new Fmt(" were completely filled",   ChatFormatting.GREEN,  "✅ Filled",           GREEN,  "filled");
            case CLAIMED          -> new Fmt(" were claimed",             ChatFormatting.WHITE,  "📦 Claimed",          GRAY,   "claimed");
            case CANCELLED        -> new Fmt(" were cancelled",           ChatFormatting.RED,    "❌ Cancelled",        RED,    "cancelled");
            case FLIPPED          -> new Fmt(" were flipped",             ChatFormatting.GREEN,  "🔃 Flipped",          GREEN,  "flipped");
        };

        MutableComponent chat = prefix.append(Component.literal(fmt.suffix()).withStyle(fmt.color()));

        // Click hint for outbid batch is still appended by the CHAT dispatcher automatically.

        DiscordPayload.Embed embed = DiscordPayload.Embed.builder(fmt.embedTitle(), fmt.embedColor())
                .description(chat.getString())
                .field("Product", subject.label())
                .field("Count",   String.format("%,d orders", distinctCount))
                .build();

        return Content.of(chat, embed,
                new Content.ScreenTitle(
                        Component.literal(distinctCount + "× " + subject.label()).withStyle(ChatFormatting.GOLD),
                        Component.literal(fmt.shortVerb()).withStyle(fmt.color())));
    }
}