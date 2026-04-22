package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceLevel;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class PageSummaryParser {
    private static final BazaarLogger LOG = BazaarLogger.of(PageSummaryParser.class);

    public record PageSummaryResult(List<PriceLevel> bidLevels, List<PriceLevel> askLevels, long observedAt) {}

    private PageSummaryParser() {}

    public static PageSummaryResult parseItemPage(ItemStack buyOrderStack, ItemStack sellOfferStack) {
        long now = System.currentTimeMillis();
        var result = new PageSummaryResult(parsePriceLevels(buyOrderStack, now), parsePriceLevels(sellOfferStack, now), now);

        PlayerLogger.debug("Page summary parsed: %d bid levels, %d ask levels".formatted(result.bidLevels().size(), result.askLevels().size()), NotificationType.SCREEN_PARSING);

        return result;
    }

    private static List<PriceLevel> parsePriceLevels(ItemStack stack, long observedAt) {
        var lines = LoreParser.lines(stack);

        LOG.info("parsePriceLevels: {} lore lines from '{}'", lines.size(), stack.getDisplayName().getString());

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

            PriceLevel result = new PriceLevel(price, volume, orders, observedAt, new DataSources.PageSummary(observedAt));

            return Optional.of(result);
        } catch (Exception exception) {
            LOG.warn("parsePriceLevel failed — siblings=[{}] error={}", siblings.stream().map(Component::getString).collect(java.util.stream.Collectors.joining(", ")), exception.getMessage());

            return Optional.empty();
        }
    }
}