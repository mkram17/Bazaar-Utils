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

public final class SellSacksParser {
    public record SellSacksResult(List<OrderInfo> items, Optional<OtherItems> otherItems) {
        public record OtherItems(int volume, double totalValue) {}
    }

    private SellSacksParser() {}

    public static SellSacksResult parseSackOrders(ItemStack sellSacksStack) {
        List<OrderInfo> items = new ArrayList<>();
        Optional<SellSacksResult.OtherItems> otherItems = Optional.empty();

        for (Component line : LoreParser.lines(sellSacksStack)) {
            List<Component> siblings = line.getSiblings();
            if (siblings.size() != 6) continue;

            String name = siblings.get(3).getString().trim();

            try {
                int volume = Util.parseNumber(siblings.get(1).getString());
                double totalPrice = Double.parseDouble(siblings.get(5).getString().replace(" coins", "").replace(",", ""));
                double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

                if (name.equals("Other items")) {
                    otherItems = Optional.of(new SellSacksResult.OtherItems(volume, totalPrice));
                } else {
                    items.add(new OrderInfo(name, TransactionType.Side.BUY, null, volume, pricePerUnit, null));
                }
            } catch (Exception ignored) {}
        }

        return new SellSacksResult(List.copyOf(items), otherItems);
    }
}