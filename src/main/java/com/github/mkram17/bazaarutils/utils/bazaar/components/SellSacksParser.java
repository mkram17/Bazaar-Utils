package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SellSacksParser {
    public record SellSacksResult(List<OrderInfo> items, Optional<OtherItems> otherItems) {
        public record OtherItems(int volume, double totalValue) {}
    }

    private SellSacksParser() {}

    /**
     * Each sellable-item entry in the sell-sacks lore:
     *   " 16,133x Enchanted Hard Stone for 8,406,906 coins"
     * Siblings: [" ", quantity(green), "x "(gray), product(variable color), "for "(gray), "NNN coins"(gold)]
     */
    private static final Pattern ITEM_LINE_PATTERN = Pattern.compile("(?<volume>[\\d,]+)x (?<product>.+?) for (?<price>[\\d,.]+) coins");

    public static SellSacksResult parseSackOrders(ItemStack sellSacksStack) {
        List<OrderInfo> items = new ArrayList<>();
        Optional<SellSacksResult.OtherItems> otherItems = Optional.empty();

        for (Component line : LoreParser.lines(sellSacksStack)) {
            Matcher matcher = ITEM_LINE_PATTERN.matcher(line.getString());
            if (!matcher.find()) continue;

            String product = matcher.group("product").trim();

            try {
                int volume = Util.parseNumber(matcher.group("volume"));
                double totalPrice = Double.parseDouble(matcher.group("price").replace(",", ""));
                double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

                if (product.equals("Other items")) {
                    otherItems = Optional.of(new SellSacksResult.OtherItems(volume, totalPrice));
                } else {
                    items.add(new OrderInfo(product, TransactionType.Side.BUY, null, volume, pricePerUnit, null));
                }
            } catch (Exception ignored) {}
        }

        return new SellSacksResult(List.copyOf(items), otherItems);
    }
}