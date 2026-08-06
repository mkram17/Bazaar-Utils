package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the "here is what you could sell right now" lore that the Instant Sell and Sell Sacks
 * buttons share. Both list one line per product and fold the tail into a single "Other items"
 * total, in the same format, so both are parsed here.
 *
 * @see com.github.mkram17.bazaarutils.data.SellableAPI
 */
public final class SellableLore {
    private SellableLore() {}

    /** Parsed contents of one sellable-items button. */
    public record Result(List<OrderInfo> items, Optional<OtherItems> otherItems) {
        public static Result empty() {
            return new Result(List.of(), Optional.empty());
        }
    }

    /**
     * Volume folded under the "Other items" aggregation line — a volume and a total value, with no
     * per-product breakdown to recover.
     */
    public record OtherItems(int volume, double totalValue) {}

    /**
     * Each sellable-item entry in the lore:
     *   " 16,133x Enchanted Hard Stone for 8,406,906 coins"
     * Siblings: [" ", quantity(green), "x "(gray), product(variable color), "for "(gray), "NNN coins"(gold)]
     */
    private static final Pattern ITEM_LINE_PATTERN = Pattern.compile("(?<volume>[\\d,]+)x (?<product>.+?) for (?<price>[\\d,.]+) coins");

    /**
     * Parses a sellable-items button for what the current screen context could sell.
     *
     * <p>Targets</p>
     * <ul>
     *  <li>{@code MAIN_PAGE | SEARCH_PAGE}: {@code BazaarSlots.OVERVIEW_PAGE.SELL_INVENTORY}, {@code .SELL_SACKS}</li>
     *  <li>{@code PRODUCTS_CATALOG_PAGE}: {@code BazaarSlots.PRODUCTS_CATALOG_PAGE.SELL_INVENTORY}, {@code .SELL_SACKS}</li>
     * </ul>
     *
     * <p>The product page's Instant Sell button reads differently and has its own parser; see
     * {@link InstantSellParser#parseProductPageOrder}.</p>
     */
    public static Result parse(ItemStack sellableStack) {
        List<OrderInfo> items = new ArrayList<>();
        Optional<OtherItems> otherItems = Optional.empty();

        for (Component line : LoreParser.lines(sellableStack)) {
            Matcher matcher = ITEM_LINE_PATTERN.matcher(line.getString());
            if (!matcher.find()) continue;

            String product = matcher.group("product").trim();

            try {
                int volume = Util.parseNumber(matcher.group("volume"));

                // Items with no buy orders appear with 0 quantity — skip them rather than
                // dividing by zero or producing a meaningless order.
                if (volume == 0) {
                    Util.logMessage("SellableLore: skipping '%s' — 0 quantity (no buy orders)".formatted(product));

                    continue;
                }

                double totalPrice = Double.parseDouble(matcher.group("price").replace(",", ""));
                double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

                if (product.equals("Other items")) {
                    otherItems = Optional.of(new OtherItems(volume, totalPrice));
                } else {
                    items.add(new OrderInfo(product, TransactionType.Side.BUY, null, volume, pricePerUnit, null));
                }
            } catch (Exception ignored) {}
        }

        return new Result(List.copyOf(items), otherItems);
    }
}
