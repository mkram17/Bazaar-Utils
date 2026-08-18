package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.data.stored.BazaarProfileFlags;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.PlayerAccountUpgrades;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @see com.github.mkram17.bazaarutils.data.SellableAPI
 */
public final class InstantSellParser {
    /** Parsed result from an Instant Sell container. */
    public record InstantSellResult(List<OrderInfo> items, Optional<OtherItems> otherItems) {
        /** Volume folded under the "Other items" aggregation line — volume and total value actionable only, no per-product breakdown. */
        public record OtherItems(int volume, double totalValue) {}
    }

    private InstantSellParser() {}

    /** Each sellable-item entry in an instant-sell lore:
     *  " 432x Fig Log for 1,296 coins"
     *  Siblings: [" ", quantity(green), "x "(gray), product(white), "for "(gray), "NNN coins"(gold)]
     */
    private static final Pattern ITEM_LINE_PATTERN = Pattern.compile("(?<volume>[\\d,]+)x (?<product>.+?) for (?<price>[\\d,.]+) coins");

    /**
     * Parses the instant-sell button present on catalog/overview pages
     * for data of what's currently sellable under the current screen context.
     *
     * <p>Targets</p>
     * <ul>
     *  <li>{@code MAIN_PAGE | SEARCH_PAGE}: {@code BazaarSlots.OVERVIEW_PAGE.SELL_INVENTORY},</li>
     *  <li>{@code PRODUCTS_CATALOG_PAGE}: {@code BazaarSlots.PRODUCTS_CATALOG_PAGE.SELL_INVENTORY}</li>
     * </ul>
     */
    public static InstantSellResult parseInstantSellOrders(ItemStack instantSellStack) {
        List<OrderInfo> items = new ArrayList<>();
        Optional<InstantSellResult.OtherItems> otherItems = Optional.empty();

        for (Component line : LoreParser.lines(instantSellStack)) {
            Matcher matcher = ITEM_LINE_PATTERN.matcher(line.getString());
            if (!matcher.find()) continue;

            String product = matcher.group("product").trim();

            try {
                int volume = Util.parseNumber(matcher.group("volume"));

                // Items with no buy orders appear with 0 quantity — skip them
                // rather than dividing by zero or producing a meaningless order.
                if (volume == 0) {
                    Util.logMessage("parseInstantSellOrders: skipping '%s' — 0 quantity (no buy orders)".formatted(product));

                    continue;
                }

                double totalPrice = Double.parseDouble(matcher.group("price").replace(",", ""));
                double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

                if (product.equals("Other items")) {
                    otherItems = Optional.of(new InstantSellResult.OtherItems(volume, totalPrice));
                } else {
                    Optional<OrderInfo> result = OrderInfo.of(product, TransactionType.Side.BUY, pricePerUnit, volume);

                    if (result.isEmpty()) {
                        PlayerActionUtil.notifyAll("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(product));

                        continue;
                    }

                    items.add(result.get());
                }
            } catch (Exception exception) {
                Util.logError("parseInstantSellOrders: failed to parse lore line — value=[%s]".formatted(line.getString()), exception);
            }

        }

        PlayerActionUtil.notifyAll("InstantSell (overview page) parsed: %d known items | %d folded to \"Other Items\"".formatted(items.size(), otherItems.map(InstantSellResult.OtherItems::volume).orElse(0)), NotificationType.GUI);

        return new InstantSellResult(List.copyOf(items), otherItems);
    }

    /** "Amount: 896x" — the "x" is a separate sibling but getString() concatenates */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("Amount: (?<amount>[\\d,]+)x");

    /** "Total: 532.2 coins" — confirmed from lore dump */
    private static final Pattern PRICE_PATTERN = Pattern.compile("Total: (?<price>[\\d,.]+) coins");

    /** Present when there are no buy orders for this item / the player doesn't hold essence to sell for it */
    private static final Pattern NO_INVENTORY_OR_ORDERS_PATTERN = Pattern.compile("No one is buying!|There are no Buy Orders!|None to sell in your inventory!");

    /** "Inventory: 896 items" or "Inventory: None" */
    private static final Pattern INVENTORY_PATTERN = Pattern.compile("Inventory: (?<inv>.+)");

    /** "Current tax: 1%" */
    private static final Pattern TAX_PATTERN = Pattern.compile("Current tax: (?<tax>[\\d.]+)%");

    /**
     * Parses the instant-sell button present on product pages
     * for data of how much of the player-held volume is sellable instantly.
     *
     * <p>Targets</p>
     * <ul>
     *  <li>{@code PRODUCT_PAGE}: {@code BazaarSlots.PRODUCT_PAGE.SELL_INSTANTLY},</li>
     * </ul>
     */
    public static Optional<InstantSellResult> parseProductPageOrder(ItemStack sellInstantlyStack, ProfileKey key) {
        List<Component> lines = LoreParser.lines(sellInstantlyStack);

        String product = lines.isEmpty() ? null : lines.getFirst().getString().trim();
        if (product != null && product.isEmpty()) {
            product = null;
        }

        String amountStr = null;
        String priceStr = null;
        String taxStr = null;
        boolean noInventory = false;
        boolean noOrders = false;

        for (Component line : lines) {
            String plain = line.getString();

            Matcher matcher;

            if (NO_INVENTORY_OR_ORDERS_PATTERN.matcher(plain).find()) {
                noOrders = true;
                break;
            }

            if (!noInventory && (matcher = INVENTORY_PATTERN.matcher(plain)).find()) {
                noInventory = matcher.group("inv").trim().startsWith("None");
            } else if (amountStr == null && (matcher = AMOUNT_PATTERN.matcher(plain)).find()) {
                amountStr = matcher.group("amount");
            } else if (priceStr == null && (matcher = PRICE_PATTERN.matcher(plain)).find()) {
                priceStr = matcher.group("price");
            } else if (taxStr == null && (matcher = TAX_PATTERN.matcher(plain)).find()) {
                taxStr = matcher.group("tax");
            }

            if (amountStr != null && priceStr != null && taxStr != null) break;
        }

        if (noInventory) {
            Util.logMessage("parseProductPageOrder: no inventory for '%s' — skipping".formatted(product));

            return Optional.empty();
        }

        if (noOrders) {
            Util.logMessage("parseProductPageOrder: no buy orders for '%s' — skipping".formatted(product));

            return Optional.empty();
        }

        if (amountStr == null || priceStr == null) {
            Util.logMessage("parseProductPageOrder: pattern miss for '%s' — amount=%s price=%s".formatted(product, amountStr, priceStr));

            return Optional.empty();
        }

        try {
            int volume = Integer.parseInt(amountStr.replace(",", "").trim());
            double totalPrice = Double.parseDouble(priceStr.replace(",", "").trim());
            double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

            Optional<OrderInfo> result = OrderInfo.of(product, TransactionType.Side.BUY, pricePerUnit, volume);

            if (result.isEmpty()) {
                PlayerActionUtil.notifyAll("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(product));

                return Optional.empty();
            }

            if (taxStr != null) {
                try {
                    reconcileTax(Double.parseDouble(taxStr.trim()), key);
                } catch (Exception e) {
                    Util.logError("parseProductPageOrder: failed to parse tax '%s'".formatted(taxStr), e);
                }
            }

            PlayerActionUtil.notifyAll("InstantSell (item page) parsed: %s %dx@%.4f".formatted(result.get().getName(), result.get().getVolume(), result.get().getPricePerItem()), NotificationType.GUI);

            return Optional.of(new InstantSellResult(List.of(result.get()), Optional.empty()));
        } catch (Exception exception) {
            Util.logError("parseProductPageOrder: arithmetic failed for '%s' — amount='%s' price='%s'".formatted(product, amountStr, priceStr), exception);

            return Optional.empty();
        }
    }

    private static void reconcileTax(double observedPercent, ProfileKey key) {
        double normalizedTax = TaxContext.normalizeObserved(observedPercent);
        var currentTier = BazaarProfileFlags.get(key).bazaarFlipperTier();

        for (PlayerAccountUpgrades.BazaarFlipper tier : PlayerAccountUpgrades.BazaarFlipper.values()) {
            if (Math.round(tier.getUserBazaarTax() * 10) == Math.round(normalizedTax * 10)) {
                if (currentTier != tier) {
                    Util.logMessage("reconcileTax: %s → %s (observed %.4g%%%s)".formatted(currentTier, tier, observedPercent, TaxContext.isQuadTaxes() ? " [quad taxes /4 → " + normalizedTax + "%]" : ""));

                    BazaarProfileFlags.markBazaarFlipperTier(key, tier);

                    PlayerActionUtil.notifyAll("Bazaar Flipper tier auto-detected as %s from observed tax.".formatted(tier.name()));
                }

                return;
            }
        }

        Util.logMessage("reconcileTax: observed %.4g%% (normalized %.4g%%) matches no BazaarFlipper tier — ignoring".formatted(observedPercent, normalizedTax));
    }
}