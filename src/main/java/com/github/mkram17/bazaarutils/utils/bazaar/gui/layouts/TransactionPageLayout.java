package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Layout utilities for the Bazaar item page.
 */
public final class TransactionPageLayout {

    public static final Pattern AMOUNT_PATTERN = Pattern.compile("Amount: (?<amount>[0-9,.]+)x");
    public static final Pattern SELL_LIMIT_PATTERN = Pattern.compile("Inventory: (?<amount>[0-9,.]+) items");
    public static final Pattern PURCHASE_LIMIT_PATTERN = Pattern.compile("Buy up to (?<amount>[0-9,.]+)x.");

    private TransactionPageLayout() {}


    public static Optional<Double> findOptionAmount(ItemStack option) {
        return LoreParser.matchDouble(option, AMOUNT_PATTERN, "amount", "option amount on " + option.getCustomName());
    }

    public static Optional<Integer> findBuyOrderAmountLimit(ItemStack inputSign) {
        return LoreParser.matchInt(inputSign, PURCHASE_LIMIT_PATTERN, "amount", "buy order limit on " + inputSign.getCustomName());
    }

    public static Optional<Integer> findSellAmountLimit(ItemStack inputSign) {
        return LoreParser.matchInt(inputSign, SELL_LIMIT_PATTERN, "amount", "sell limit on " + inputSign.getCustomName());
    }
}