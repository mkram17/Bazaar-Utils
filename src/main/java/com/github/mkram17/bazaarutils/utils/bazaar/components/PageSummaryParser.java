package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageSummaryParser {

    /**
     * "- 2.0 coins each | 714,072x in 10 orders" (buy book) / "- 14.6 coins each | 37x from 1 offer" (sell book).
     */
    private static final Pattern PRICE_LEVEL_PATTERN = Pattern.compile("- (?<price>[\\d,.]+) coins each \\| (?<volume>[\\d,]+)x (?:in|from) (?<orders>\\d+) (?:orders?|offers?)");

    public record PageSummaryResult(List<ParsedLevel> bidLevels, List<ParsedLevel> askLevels) {
        public record ParsedLevel(double pricePerUnit, int volume, int orderCount) {}
    }

    public static PageSummaryResult parseItemPage(ItemStack buyOrderStack, ItemStack sellOfferStack) {
        var result = new PageSummaryResult(
                parsePriceLevels(buyOrderStack),
                parsePriceLevels(sellOfferStack));

        PlayerActionUtil.notifyAll(
                "Page summary parsed: %d bid levels, %d ask levels".formatted(
                        result.bidLevels().size(), result.askLevels().size()),
                NotificationType.GUI);

        return result;
    }

    private static List<PageSummaryResult.ParsedLevel> parsePriceLevels(ItemStack stack) {
        return LoreParser.lines(stack).stream()
                .map(PageSummaryParser::parseLevel)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<PageSummaryResult.ParsedLevel> parseLevel(Component line) {
        Matcher matcher = PRICE_LEVEL_PATTERN.matcher(line.getString());
        if (!matcher.find()) return Optional.empty();

        try {
            double price = Double.parseDouble(matcher.group("price").replace(",", "").trim());
            int volume = Integer.parseInt(matcher.group("volume").replace(",", "").trim());
            int orders = Integer.parseInt(matcher.group("orders"));

            return Optional.of(new PageSummaryResult.ParsedLevel(price, volume, orders));
        } catch (Exception exception) {
            Util.logError("parseLevel: pattern matched but arithmetic failed — line='%s'".formatted(line.getString()), exception);

            return Optional.empty();
        }
    }
}