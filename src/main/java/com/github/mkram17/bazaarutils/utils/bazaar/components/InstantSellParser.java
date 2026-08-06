package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.PlayerAccountUpgrades;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the product page's Instant Sell button. The catalog and overview pages phrase the same
 * offer as a per-product list, which {@link SellableLore} reads instead.
 *
 * @see com.github.mkram17.bazaarutils.data.SellableAPI
 */
public final class InstantSellParser {
    private InstantSellParser() {}

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
    public static Optional<SellableLore.Result> parseProductPageOrder(ItemStack sellInstantlyStack) {
        List<Component> lines = LoreParser.lines(sellInstantlyStack);

        // Name is still read positionally — line 0 is always the item name
        // and is structurally stable across both the orders/no-orders cases.
        String product = lines.isEmpty() ? null
                : lines.getFirst().getSiblings().stream()
                  .map(Component::getString)
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .findFirst()
                  .orElse(null);

        if (product == null) {
            Util.logMessage("parseProductPageOrder: could not read item name from lore");

            return Optional.empty();
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

            OrderInfo result = new OrderInfo(product, TransactionType.Side.BUY, null, volume, pricePerUnit, null);

            if (taxStr != null) {
                try {
                    reconcileTax(Double.parseDouble(taxStr.trim()));
                } catch (Exception e) {
                    Util.logError("parseProductPageOrder: failed to parse tax '%s'".formatted(taxStr), e);
                }
            }

            PlayerActionUtil.notifyAll("InstantSell (item page) parsed: %s %dx@%.4f".formatted(product, result.getVolume(), result.getPricePerItem()), NotificationType.GUI);

            return Optional.of(new SellableLore.Result(List.of(result), Optional.empty()));

        } catch (Exception exception) {
            Util.logError("parseProductPageOrder: arithmetic failed for '%s' — amount='%s' price='%s'".formatted(product, amountStr, priceStr), exception);

            return Optional.empty();
        }
    }

    private static void reconcileTax(double observedPercent) {
        double normalizedTax = TaxContext.normalizeObserved(observedPercent);

        for (PlayerAccountUpgrades.BazaarFlipper tier : PlayerAccountUpgrades.BazaarFlipper.values()) {
            if (Math.round(tier.getUserBazaarTax() * 10) == Math.round(normalizedTax * 10)) {
                if (BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE != tier) {
                    Util.logMessage("reconcileTax: %s → %s (observed %.4g%%%s)".formatted(BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE, tier, observedPercent, TaxContext.isQuadTaxes() ? " [quad taxes /4 → " + normalizedTax + "%]" : ""));

                    BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE = tier;
                    ConfigUtil.scheduleConfigSave();

                    PlayerActionUtil.notifyAll("Bazaar Flipper tier auto-detected as %s from observed tax; saved to your configuration file.".formatted(tier.name()));
                }

                return;
            }
        }

        Util.logMessage("reconcileTax: observed %.4g%% (normalized %.4g%%) matches no BazaarFlipper tier — ignoring".formatted(observedPercent, normalizedTax));
    }
}