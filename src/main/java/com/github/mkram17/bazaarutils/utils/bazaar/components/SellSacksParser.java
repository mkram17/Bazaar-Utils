package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @see com.github.mkram17.bazaarutils.data.SellableAPI
 */
public final class SellSacksParser {
    private static final BazaarLogger LOG = BazaarLogger.of(SellSacksParser.class);

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

    /**
     * Parses the sell-sacks button present on catalog/overview pages
     * for data of what's currently sellable under the current screen context.
     *
     * <p>Targets</p>
     * <ul>
     *  <li>{@code MAIN_PAGE | SEARCH_PAGE}: {@code BazaarSlots.OVERVIEW_PAGE.SELL_SACKS},</li>
     *  <li>{@code PRODUCTS_CATALOG_PAGE}: {@code BazaarSlots.PRODUCTS_CATALOG_PAGE.SELL_SACKS}</li>
     * </ul>
     */
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
                    Optional<OrderInfo> result = OrderInfo.of(product, TransactionType.Side.BUY, pricePerUnit, volume);

                    if (result.isEmpty()) {
                        PlayerLogger.send("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(product));

                        continue;
                    }

                    items.add(result.get());
                }
            } catch (Exception exception) {
                LOG.warn("parseSacksOrders: failed to parse lore line — value=[{}]", line.getString(), exception);
            }
        }

        PlayerLogger.debug("SellSacks parsed: %d known items | %d folded to \"Other Items\"".formatted(items.size(), otherItems.map(SellSacksResult.OtherItems::volume).orElse(0)), NotificationType.SCREEN_PARSING, LOG);

        return new SellSacksResult(List.copyOf(items), otherItems);
    }
}