package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceLevel;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class PageSummaryParser {
    public record PageSummaryResult(List<PriceLevel> bidLevels, List<PriceLevel> askLevels, long observedAt) {}

    private PageSummaryParser() {}

    public static PageSummaryResult parseItemPage(ItemStack buyOrderStack, ItemStack sellOfferStack) {
        long now = System.currentTimeMillis();

        return new PageSummaryResult(parsePriceLevels(buyOrderStack, now), parsePriceLevels(sellOfferStack, now), now);
    }

    private static List<PriceLevel> parsePriceLevels(ItemStack stack, long observedAt) {
        var lines = LoreParser.lines(stack);

        PlayerActionUtil.notifyAll("Parsing " + lines.size() + " lore lines from: " + stack.getDisplayName().getString(), NotificationType.BAZAARDATA);

        return lines.stream()
                .peek(line -> PlayerActionUtil.notifyAll(
                        "Line siblings=" + line.getSiblings().size()
                                + " | " + line.getSiblings().stream()
                                .map(Component::getString)
                                .collect(java.util.stream.Collectors.joining(", ")),
                        NotificationType.BAZAARDATA))
                .filter(line -> line.getSiblings().size() == 8)
                .map(line -> parsePriceLevel(line, observedAt))
                .peek(pool -> {
                    if (pool.isEmpty()) PlayerActionUtil.notifyAll("parsePriceLevel returned empty on a size-8 line", NotificationType.BAZAARDATA);
                })
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<PriceLevel> parsePriceLevel(Component line, long observedAt) {
        var siblings = line.getSiblings();

        if (siblings.size() != 8) return Optional.empty();

        try {
            double price = Double.parseDouble(siblings.get(1).getString().replace(" coins ", "").replace(",", "").trim());
            int volume = Integer.parseInt(siblings.get(3).getString().replace(",", "").trim());
            int orders = Integer.parseInt(siblings.get(6).getString().trim());

            PlayerActionUtil.notifyAll("Parsed level: price=" + price + " vol=" + volume + " orders=" + orders, NotificationType.BAZAARDATA);

            PriceLevel result = new PriceLevel(price, volume, orders, observedAt, new DataSources.PageSummary(observedAt));

            return Optional.of(result);
        } catch (Exception e) {
            PlayerActionUtil.notifyAll(
                    "Parse exception on siblings: "
                            + siblings.stream().map(Component::getString).collect(java.util.stream.Collectors.joining(", "))
                            + " | " + e.getMessage(),
                    NotificationType.BAZAARDATA);

            return Optional.empty();
        }
    }
}