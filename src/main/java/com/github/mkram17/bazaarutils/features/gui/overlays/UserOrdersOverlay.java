package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.data.stored.BookmarksStorage;
import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.Bookmark;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.TextDisplayWidget;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Lists the player's tracked orders beside the main Bazaar screen, so their status is readable
 * without opening the orders menu.
 */
@Module
public class UserOrdersOverlay implements ToggleableFeature {
    /** Which flank of the Bazaar GUI the list is anchored to. */
    public enum Side implements Translatable {
        LEFT,
        RIGHT;

        @Override
        public String getTranslationKey() {
            return "bazaarutils.config.overlays.user_orders_overlay.side." + name().toLowerCase(Locale.ROOT) + ".label";
        }
    }

    private static final int LINE_HEIGHT = 10;
    private static final int OVERLAY_WIDTH = 220;
    private static final int SPACING = 4;
    private static final int MAX_NAME_LENGTH = 14;

    /** Needs attention first: outbid, then anything with goods waiting, then closest to filling. */
    private static final Comparator<Order> BY_URGENCY =
            Comparator.<Order>comparingInt(UserOrdersOverlay::rank)
                    .thenComparing(Comparator.<Order>comparingDouble(UserOrdersOverlay::fillFraction).reversed());

    @Override
    public boolean isEnabled() {
        return OverlaysConfig.USER_ORDERS_OVERLAY_TOGGLE;
    }

    public UserOrdersOverlay() {}

    @RegisterWidget
    public static List<TextDisplayWidget> getWidgets() {
        if (!BazaarUtilsModules.UserOrdersOverlay.isEnabled()) {
            return Collections.emptyList();
        }

        var dimensions = WidgetManager.getScreenDimensions(BazaarScreenType.MAIN_PAGE);
        if (dimensions.isEmpty()) return Collections.emptyList();

        List<Order> orders = OrderUtil.getUserOrders().stream().sorted(BY_URGENCY).toList();
        if (orders.isEmpty()) return Collections.emptyList();

        int shown = Math.min(orders.size(), Math.max(1, OverlaysConfig.USER_ORDERS_OVERLAY_MAX_ROWS));
        int hidden = orders.size() - shown;
        int total = orders.size();

        int x = originX(dimensions.get());
        int y = dimensions.get().y() + SPACING;

        TextDisplayWidget.Alignment alignment = OverlaysConfig.USER_ORDERS_OVERLAY_SIDE == Side.LEFT
                ? TextDisplayWidget.Alignment.RIGHT
                : TextDisplayWidget.Alignment.LEFT;

        List<TextDisplayWidget> widgets = new ArrayList<>();
        widgets.add(row(x, y, alignment, () -> header(total)));

        for (int i = 0; i < shown; i++) {
            Order order = orders.get(i);

            widgets.add(row(x, y + (i + 1) * LINE_HEIGHT, alignment, () -> describe(order)));
        }

        if (hidden > 0) {
            widgets.add(row(x, y + (shown + 1) * LINE_HEIGHT, alignment,
                    () -> Component.literal("… +" + hidden + " more").withStyle(ChatFormatting.DARK_GRAY)));
        }

        return widgets;
    }

    private static TextDisplayWidget row(int x, int y, TextDisplayWidget.Alignment alignment, Supplier<Component> text) {
        return new TextDisplayWidget(x, y, OVERLAY_WIDTH, LINE_HEIGHT, text, alignment);
    }

    private static int originX(WidgetManager.ScreenWidgetDimensions dimensions) {
        if (OverlaysConfig.USER_ORDERS_OVERLAY_SIDE == Side.LEFT) {
            return dimensions.x() - SPACING - modButtonColumnWidth() - OVERLAY_WIDTH;
        }

        return dimensions.x() + dimensions.backgroundWidth() + SPACING + bookmarkColumnWidth();
    }

    /** Horizontal space {@code ModButtons} already claims on the left flank. */
    private static int modButtonColumnWidth() {
        ButtonsConfig.WidgetButton settings = ButtonsConfig.OPEN_SETTINGS_BUTTON;
        ButtonsConfig.WidgetButton orders = ButtonsConfig.OPEN_ORDERS_BUTTON;

        if (settings.isEnabled()) return settings.size + settings.spacing;
        if (orders.isEnabled()) return orders.size + orders.spacing;

        return 0;
    }

    /** Horizontal space the bookmark column already claims on the right flank. */
    private static int bookmarkColumnWidth() {
        ButtonsConfig.WidgetButton bookmark = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON;
        List<Bookmark> bookmarks = BookmarksStorage.INSTANCE.get();

        if (!bookmark.isEnabled() || bookmarks == null || bookmarks.isEmpty()) return 0;

        return bookmark.size + bookmark.spacing;
    }

    private static Component header(int total) {
        return Component.literal("Your Orders ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("(" + total + ")")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(false)));
    }

    private static Component describe(Order order) {
        TransactionType.Side side = order.getTransactionType().getSide();
        boolean buying = side == TransactionType.Side.BUY;

        MutableComponent line = Component.literal(buying ? "BUY " : "SELL ")
                .withStyle(buying ? ChatFormatting.GREEN : ChatFormatting.GOLD);

        Integer volume = order.getVolume();
        if (volume != null) {
            line.append(Component.literal(String.format("%,d", volume) + "x ").withStyle(ChatFormatting.DARK_GRAY));
        }

        line.append(Component.literal(shorten(order.getName())).setStyle(styleOf(order)));

        line.append(Component.literal(" " + Math.round(fillFraction(order) * 100) + "%")
                .withStyle(claimable(order) > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));

        Double price = order.getPricePerItem();
        if (price != null) {
            line.append(Component.literal(" @" + Util.getPrettyString(price)).withStyle(ChatFormatting.DARK_GRAY));
        }

        return line;
    }

    /**
     * Reuses the order-status highlight colours so a row reads the same as the slot it stands for.
     * Masked to 24 bits — those config values carry an alpha byte that {@link TextColor} would
     * otherwise fold into the hue.
     */
    private static Style styleOf(Order order) {
        if (order.getStatus() == OrderStatus.FILLED) {
            return Style.EMPTY.withColor(ChatFormatting.AQUA);
        }

        PricingPosition position = order.getPricingPosition();
        if (position == null) return Style.EMPTY.withColor(ChatFormatting.WHITE);

        int argb = switch (position) {
            case COMPETITIVE -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR;
            case MATCHED -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR;
            case OUTBID -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR;
        };

        return Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF));
    }

    private static String shorten(String name) {
        if (name == null) return "?";
        if (name.length() <= MAX_NAME_LENGTH) return name;

        return name.substring(0, MAX_NAME_LENGTH - 1) + "…";
    }

    private static int rank(Order order) {
        if (order.getPricingPosition() == PricingPosition.OUTBID) return 0;
        if (claimable(order) > 0) return 1;

        return 2;
    }

    private static double fillFraction(Order order) {
        Integer volume = order.getVolume();
        if (volume == null || volume <= 0) return 0;

        return (double) order.getAmountFilled() / volume;
    }

    private static int claimable(Order order) {
        return Math.max(0, order.getAmountFilled() - order.getAmountClaimed());
    }
}
