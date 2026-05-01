package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class PageSummaryParser {

    public record PageSummaryResult(List<PriceLevel> bidLevels, List<PriceLevel> askLevels, long observedAt) {}

    private PageSummaryParser() {}

    public static PageSummaryResult parseItemPage(ItemStack buyOrderStack, ItemStack sellOfferStack, long now) {
        var result = new PageSummaryResult(parsePriceLevels(buyOrderStack, now), parsePriceLevels(sellOfferStack, now), now);

        PlayerActionUtil.notifyAll("Page summary parsed: %d bid levels, %d ask levels".formatted(result.bidLevels().size(), result.askLevels().size()), NotificationType.GUI);

        return result;
    }

    private static List<PriceLevel> parsePriceLevels(ItemStack stack, long observedAt) {
        var lines = LoreParser.lines(stack);

        Util.logMessage("parsePriceLevels: %d lore lines from '%s'".formatted(lines.size(), stack.getDisplayName().getString()));

        return lines.stream()
                .filter(line -> line.getSiblings().size() == 8)
                .map(line -> parsePriceLevel(line, observedAt))
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

            PriceLevel result = new PriceLevel(price, volume, orders, new BazaarDataOrigin.PageSummary(observedAt));

            return Optional.of(result);
        } catch (Exception exception) {
            Util.logError("parsePriceLevel failed — siblings=[%s]".formatted(siblings.stream().map(Component::getString).collect(java.util.stream.Collectors.joining(", "))), exception);

            return Optional.empty();
        }
    }
}