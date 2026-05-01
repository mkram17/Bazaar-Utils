package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.data.bazaar.book.BookLevels;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageSummaryParser {

    /**
     * "- 2.0 coins each | 714,072x in 10 orders" (buy book) / "- 14.6 coins each | 37x from 1 offer" (sell book).
     */
    private static final Pattern PRICE_LEVEL_PATTERN = Pattern.compile("- (?<price>[\\d,.]+) coins each \\| (?<volume>[\\d,]+)x (?:in|from) (?<orders>\\d+) (?:orders?|offers?)");
    public record PageSummaryResult(List<PriceLevel> bidLevels, List<PriceLevel> askLevels, long observedAt) {}

    private PageSummaryParser() {}

    public static BookLevels parseItemPage(ItemStack buyOrderStack, ItemStack sellOfferStack, BazaarDataOrigin origin) {
        var bidsLevels = parsePriceLevels(buyOrderStack, origin);
        var asksLevels = parsePriceLevels(sellOfferStack, origin);

        var result = new BookLevels(asksLevels, bidsLevels);

        PlayerActionUtil.notifyAll("Page summary parsed: %d bid levels, %d ask levels".formatted(result.bidsLevels().size(), result.asksLevels().size()), NotificationType.GUI);

        return result;
    }

    private static List<PriceLevel> parsePriceLevels(ItemStack stack, BazaarDataOrigin origin) {
        var lines = LoreParser.lines(stack);

        return lines.stream()
                .map(line -> parsePriceLevel(line, origin))
                .flatMap(Optional::stream)
                .toList();
    }
    private static Optional<PriceLevel> parsePriceLevel(Component line, BazaarDataOrigin origin) {
        Matcher matcher = PRICE_LEVEL_PATTERN.matcher(line.getString());
        if (!matcher.find()) return Optional.empty();

        try {
            double price = Double.parseDouble(matcher.group("price").replace(",", "").trim());
            int volume = Integer.parseInt(matcher.group("volume").replace(",", "").trim());
            int orders = Integer.parseInt(matcher.group("orders"));

            return Optional.of(new PriceLevel(price, volume, orders, origin));
        } catch (Exception exception) {
            Util.logError("parsePriceLevel: pattern matched but arithmetic failed — line='%s'".formatted(line.getString()), exception);

            return Optional.empty();
        }
    }
}