package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@link OrderInfo} from the "Confirm Buy Order" / "Confirm Sell Offer"
 * confirmation item stacks.
 *
 * <p>The confirmation lore contains the exact, pre-tax price per unit as
 * displayed on the "Price per unit:" line, so no tax back-calculation is needed.
 *
 * <p>Buy lore shape (relevant lines only):
 * <pre>
 *   Price per unit: 3.8 coins
 *   Order: 71,680x Fig Log
 * </pre>
 *
 * <p>Sell lore shape (relevant lines only):
 * <pre>
 *   Price per unit: 4.6 coins
 *   Selling: 291x Fig Log
 * </pre>
 */
public final class TransactionConfirmationParser {

    /** "Price per unit: 3.8 coins" — the raw pre-tax unit price. */
    private static final Pattern PRICE_PATTERN = Pattern.compile("Price per unit: (?<price>[\\d,.]+) coins");

    /** "Order: 71,680x Fig Log" */
    private static final Pattern BUY_AMOUNT_PATTERN = Pattern.compile("Order: (?<volume>[\\d,]+)x (?<product>.+)");

    /** "Selling: 291x Fig Log" */
    private static final Pattern SELL_AMOUNT_PATTERN = Pattern.compile("Selling: (?<volume>[\\d,]+)x (?<product>.+)");

    private TransactionConfirmationParser() {}

    public static Optional<OrderInfo> parseBuyOrder(@Nullable ItemStack itemStack) {
        return parse(itemStack, TransactionType.Side.BUY, BUY_AMOUNT_PATTERN);
    }

    public static Optional<OrderInfo> parseSellOffer(@Nullable ItemStack itemStack) {
        return parse(itemStack, TransactionType.Side.SELL, SELL_AMOUNT_PATTERN);
    }

    private static Optional<OrderInfo> parse(@Nullable ItemStack itemStack, TransactionType.Side side, Pattern amountPattern) {
        if (itemStack == null || itemStack.isEmpty()) return Optional.empty();

        List<Component> lore = LoreParser.lines(itemStack);

        String product = null;
        String priceStr = null;
        String volumeStr = null;

        for (Component line : lore) {
            String plain = line.getString();
            Matcher matcher;

            if (priceStr == null && (matcher = PRICE_PATTERN.matcher(plain)).find()) {
                priceStr = matcher.group("price");
            } else if (volumeStr == null && (matcher = amountPattern.matcher(plain)).find()) {
                volumeStr = matcher.group("volume");
                product = matcher.group("product").trim();
            }

            if (priceStr != null && volumeStr != null) break;
        }

        if (priceStr == null || volumeStr == null || product == null) {
            Util.logMessage("parse: pattern miss (side=%s) — price='%s' volume='%s' product='%s'".formatted(side, priceStr, volumeStr, product));

            return Optional.empty();
        }

        try {
            double price = Double.parseDouble(priceStr.replace(",", "").trim());
            int volume = Integer.parseInt(volumeStr.replace(",", "").trim());

            return OrderInfo.of(product, side, price, volume);
        } catch (Exception exception) {
            Util.logError("parse: arithmetic failed (side=%s) — price='%s' volume='%s'".formatted(side, priceStr, volumeStr), exception);

            return Optional.empty();
        }
    }
}