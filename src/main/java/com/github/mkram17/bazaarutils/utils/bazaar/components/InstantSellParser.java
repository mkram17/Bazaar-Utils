package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

public final class InstantSellParser {
    public record InstantSellResult(List<OrderInfo> items) {}

    private InstantSellParser() {}

    public static InstantSellResult parseOrders(ItemStack instantSellStack) {
        List<OrderInfo> items = LoreParser.lines(instantSellStack).stream()
                .filter(line -> line.getSiblings().size() == 6)
                .map(InstantSellParser::parseLine)
                .flatMap(Optional::stream)
                .toList();

        return new InstantSellResult(items);
    }

    public static Optional<InstantSellResult> parseItemPageOrder(ItemStack sellInstantlyStack) {
        List<Text> lines = LoreParser.lines(sellInstantlyStack);
        if (lines.size() < 6) return Optional.empty();

        try {
            String name = lines.get(0).getSiblings().getFirst().getString().trim();
            int volume = Util.parseNumber(lines.get(4).getSiblings().get(1).getString());
            double totalPrice = Double.parseDouble(lines.get(5).getSiblings().get(1).getString().replace(" coins", "").replace(",", ""));
            double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

            return Optional.of(new InstantSellResult(List.of(new OrderInfo(name, OrderType.BUY, null, volume, pricePerUnit, null))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<OrderInfo> parseLine(Text line) {
        List<Text> s = line.getSiblings();
        if (s.size() != 6) return Optional.empty();

        String name = s.get(3).getString().trim();

        try {
            int volume = Util.parseNumber(s.get(1).getString());
            double totalPrice = Double.parseDouble(s.get(5).getString().replace(" coins", "").replace(",", ""));
            double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

            return Optional.of(new OrderInfo(name, OrderType.BUY, null, volume, pricePerUnit, null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}