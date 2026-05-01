package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared parsed output for sellable item-line lists (Instant Sell overview and Sell Sacks).
 */
public record SellableItemList(
        List<OrderInfo> items,
        Optional<OtherItems> otherItems
) {
    public static final SellableItemList EMPTY = new SellableItemList(List.of(), Optional.empty());

    public SellableItemList {
        items = List.copyOf(items);
    }

    /** Volume folded under the "Other items" aggregation line — volume and total value actionable only, no per-product breakdown. */
    public record OtherItems(int volume, double totalValue) {}

    /**
     * Each sellable-item entry in instant-sell / sell-sacks lore:
     * {@code " 432x Fig Log for 1,296 coins"}
     */
    private static final Pattern ITEM_LINE_PATTERN =
            Pattern.compile("(?<volume>[\\d,]+)x (?<product>.+?) for (?<price>[\\d,.]+) coins");

    /** Shared item-line parser used by both {@link InstantSellParser#parseInstantSellOrders} and {@link SellSacksParser#parseSackOrders}. */
    static SellableItemList parse(ItemStack stack, String logContext) {
        List<OrderInfo> items = new ArrayList<>();
        Optional<OtherItems> otherItems = Optional.empty();

        for (Component line : LoreParser.lines(stack)) {
            String plain = Util.stripFormatCodes(line.getString());
            Matcher matcher = ITEM_LINE_PATTERN.matcher(plain);
            if (!matcher.find()) continue;

            String product = matcher.group("product").trim();

            try {
                int volume = Util.parseNumber(matcher.group("volume"));

                // Items with no buy orders appear with 0 quantity — skip them
                // rather than dividing by zero or producing a meaningless order.
                if (volume <= 0) {
                    Util.logMessage("%s: skipping '%s' — non-positive quantity (%d)".formatted(logContext, product, volume));
                    continue;
                }

                double totalPrice = Double.parseDouble(matcher.group("price").replace(",", ""));
                double pricePerUnit = Util.truncateNum(totalPrice / volume);

                if (product.equals("Other items")) {
                    otherItems = Optional.of(new OtherItems(volume, totalPrice));
                } else {
                    Optional<OrderInfo> result = OrderInfo.of(product, TransactionType.Side.BUY, pricePerUnit, volume, false);

                    if (result.isEmpty()) {
                        PlayerActionUtil.notifyAll("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(product));
                        continue;
                    }

                    items.add(result.get());
                }
            } catch (Exception exception) {
                Util.logError("%s: failed to parse lore line — value=[%s]".formatted(logContext, plain), exception);
            }
        }

        return new SellableItemList(items, otherItems);
    }
}
