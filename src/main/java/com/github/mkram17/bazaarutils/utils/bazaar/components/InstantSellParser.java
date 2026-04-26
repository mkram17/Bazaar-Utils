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

public final class InstantSellParser {
    public record InstantSellResult(List<OrderInfo> items, Optional<OtherItems> otherItems) {
        public record OtherItems(int volume, double totalValue) {}
    }

    private InstantSellParser() {}

    public static InstantSellResult parseInstantSellOrders(ItemStack instantSellStack) {
        List<OrderInfo> items = new ArrayList<>();
        Optional<InstantSellResult.OtherItems> otherItems = Optional.empty();

        for (Component line : LoreParser.lines(instantSellStack)) {
            List<Component> siblings = line.getSiblings();
            if (siblings.size() != 6) continue;

            String name = siblings.get(3).getString().trim();

            try {
                int volume = Util.parseNumber(siblings.get(1).getString());
                double totalPrice = Double.parseDouble(siblings.get(5).getString().replace(" coins", "").replace(",", ""));
                double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

                if (name.equals("Other items")) {
                    otherItems = Optional.of(new InstantSellResult.OtherItems(volume, totalPrice));
                } else {
                    items.add(new OrderInfo(name, TransactionType.Side.BUY, null, volume, pricePerUnit, null));
                }
            } catch (Exception ignored) {}
        }

        return new InstantSellResult(List.copyOf(items), otherItems);
    }

    public static Optional<InstantSellResult> parseProductPageOrder(ItemStack sellInstantlyStack) {
        List<Component> lines = LoreParser.lines(sellInstantlyStack);
        if (lines.size() < 6) return Optional.empty();

        try {
            String name = lines.get(0).getSiblings().getFirst().getString().trim();
            int volume = Util.parseNumber(lines.get(4).getSiblings().get(1).getString());
            double totalPrice = Double.parseDouble(lines.get(5).getSiblings().get(1).getString().replace(" coins", "").replace(",", ""));
            double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;
            return Optional.of(new InstantSellResult(List.of(new OrderInfo(name, TransactionType.Side.BUY, null, volume, pricePerUnit, null)), Optional.empty()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}