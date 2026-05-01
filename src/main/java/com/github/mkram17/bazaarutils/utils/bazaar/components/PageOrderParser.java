package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts Bazaar order data from the Orders page container lore.
 *
 * <p>Only slots that pass {@link OrdersPageLayout#isOrderSlot} are considered;
 * frame and border glass slots are structurally excluded before any parsing.
 */
public final class PageOrderParser {
    /**
     * Maximum units by which a k/M-abbreviated fill line can under-report the true value.
     * Hypixel displays "71.7k" for any fill in [71,700 … 71,799], so the parsed integer
     * floors to the nearest 100. Consumers reasoning about fill precision use this bound.
     */
    public static final int FILL_TRUNCATION_MAX = 100;

    /**
     * Claimable display is exact below this threshold. At or above 10k units Hypixel
     * abbreviates with the same k/M shorthand as fill, introducing up to
     * FILL_TRUNCATION_MAX − 1 units of undercount error in the raw claimable value.
     */
    public static final int CLAIM_TRUNCATION_THRESHOLD = 10_000;

    /**
     * "Order amount: 71,680x" (buy) or "Offer amount: 16x" (sell).
     * Hypixel never abbreviates this line.
     */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:Order|Offer) amount: (?<amount>[\\d,]+)x");

    /**
     * "Filled: 1.4k/71.7k (2%)" or "Filled: 71.7k/71.7k 100%!"
     * Captures the numerator only; denominator is redundant.
     * Absent when nothing has filled yet.
     */
    private static final Pattern FILLED_PATTERN = Pattern.compile("Filled: (?<filled>[\\d,.kKmM]+)/");

    /**
     * Detects the bold "100%!" completion marker.
     * When present, {@link #resolveScreenFill} short-circuits to {@code totalAmount}.
     */
    private static final Pattern FULL_FILL_PATTERN = Pattern.compile("100%!");

    /** "Price per unit: 2.8 coins" */
    private static final Pattern PRICE_PATTERN = Pattern.compile("Price per unit: (?<price>[\\d,.]+) coins");

    /**
     * Present on all orders when the profile is in a co-op.
     * "By: [MVP+] PlayerUsername" or "By: PlayerUsername" (rankless).
     * Absent entirely when the profile is not in a co-op.
     */
    private static final Pattern COOP_AUTHOR_PATTERN = Pattern.compile("By: (?:\\[.*?] )?(?<username>\\S+)");

    /**
     * BUY order: "You have 1,429 items to claim!"
     * Value is in item units; may use k/M shorthand.
     */
    private static final Pattern BUY_CLAIM_PATTERN = Pattern.compile("You have (?<claimable>[\\d,.kKmM]+) items to claim!");

    /**
     * SELL offer: "You have 74.4 coins to claim!"
     * Value is post-tax coins; unit count is back-calculated in {@link #resolveRawClaimableUnits}.
     */
    private static final Pattern SELL_CLAIM_PATTERN = Pattern.compile("You have (?<claimable>[\\d,.kKmM]+) coins to claim!");

    /**
     * Pairs a resolved {@link OrderInfo} with its screen-observed fill and claim state.
     *
     * <dl>
     *   <dt>{@code filledAmount}</dt>
     *   <dd>Units filled so far, clamped to {@code totalAmount} to absorb k/M rounding.
     *       Zero when the "Filled:" line is absent.</dd>
     *   <dt>{@code claimableAmount}</dt>
     *   <dd>Units available to claim. Zero when the claim line is absent.</dd>
     * </dl>
     *
     * {@link #claimedAmount()} {@code = filledAmount − claimableAmount}
     */
    public record ParsedEntry(OrderInfo info, ItemInfo item, int filledAmount, int claimableAmount, boolean coopOrder) {

        /** Units that have been filled and already claimed. Always {@code >= 0}. */
        public int claimedAmount() {
            return Math.max(0, filledAmount - claimableAmount);
        }
    }

    private PageOrderParser() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public static List<ParsedEntry> parse(List<ItemInfo> items, int containerSize, String localPlayerName) {
        var result = items.stream()
                .filter(item -> !item.isEmpty())
                .filter(item -> OrdersPageLayout.isOrderSlot(item.slotIndex(), containerSize))
                .map(item -> parseEntry(item, localPlayerName))
                .filter(Objects::nonNull)
                .toList();

        if (NotificationType.GUI.isEnabled()) {
            for (var entry : result) {
                PlayerActionUtil.notifyAll(
                        "Slot#%d coop=%b → %s %s %dx @ %.4f | filled=%d claimable=%d claimed=%d".formatted(
                                entry.item().slotIndex(),
                                entry.coopOrder(),
                                entry.info().getProductId(),
                                entry.info().getTransaction().getSide(),
                                entry.info().getVolume(),
                                entry.info().getPricePerItem(),
                                entry.filledAmount(),
                                entry.claimableAmount(),
                                entry.claimedAmount()),
                        NotificationType.GUI);
            }
        }

        return result;
    }

    private static @Nullable ParsedEntry parseEntry(ItemInfo item, String localPlayerName) {
        var name = item.itemStack().getCustomName();
        if (name == null) {
            Util.logMessage("Slot#%d → null custom name (unexpected after slot filter)".formatted(item.slotIndex()));

            return null;
        }

        TransactionType.Side side = parseSide(name);
        if (side == null) {
            // All decoration slots are pre-filtered — null side here means Hypixel changed the format.
            Util.logMessage("Slot#%d → null side (unexpected after slot filter) — name='%s'".formatted(item.slotIndex(), name.getString()));

            return null;
        }

        String itemName = name.getSiblings().stream()
                .filter(t -> !t.getStyle().isBold())
                .map(Component::getString)
                .map(String::strip)
                .findFirst()
                .orElse("")
                .strip();

        List<Component> lore = LoreParser.lines(item.itemStack());

        String priceStr = null;
        String totalStr = null;
        String claimStr = null;
        String filledStr = null;
        String byUsername = null;
        boolean fullyFilled = false;

        Pattern pattern = side == TransactionType.Side.BUY
                ? BUY_CLAIM_PATTERN : SELL_CLAIM_PATTERN;

        for (Component line : lore) {
            String plain = line.getString();
            Matcher matcher;

            if (totalStr == null && (matcher = AMOUNT_PATTERN.matcher(plain)).find()) {
                totalStr = matcher.group("amount");
            } else if (filledStr == null && (matcher = FILLED_PATTERN.matcher(plain)).find()) {
                filledStr   = matcher.group("filled");
                fullyFilled = FULL_FILL_PATTERN.matcher(plain).find();
            } else if (priceStr == null && (matcher = PRICE_PATTERN.matcher(plain)).find()) {
                priceStr = matcher.group("price");
            } else if (claimStr == null && (matcher = pattern.matcher(plain)).find()) {
                claimStr = matcher.group("claimable");
            } else if (byUsername == null && (matcher = COOP_AUTHOR_PATTERN.matcher(plain)).find()) {
                byUsername = matcher.group("username");
            }

            if (totalStr != null && filledStr != null && priceStr != null && claimStr != null) break;
        }

        if (totalStr == null || priceStr == null) {
            Util.logMessage("Slot#%d → pattern miss — lore=%s".formatted(item.slotIndex(), lore.toString()));

            return null;
        }

        int totalAmount = Integer.parseInt(totalStr.replace(",", "").trim());
        double price = Double.parseDouble(priceStr.replace(",", "").trim());
        int filledAmount = resolveScreenFill(filledStr, fullyFilled, totalAmount);
        int rawClaimable = resolveRawClaimableUnits(side, claimStr, price);

        int clampedClaimable = Math.min(rawClaimable, totalAmount);
        int correctedFill = Math.clamp(filledAmount, clampedClaimable, totalAmount);
        int claimableAmount  = Math.clamp(rawClaimable, 0, correctedFill);

        Optional<OrderInfo> info = OrderInfo.of(itemName, side, price, totalAmount);
        if (info.isEmpty()) {
            Util.logMessage("Slot#%d → name resolution failed for '%s'".formatted(item.slotIndex(), itemName));
            PlayerActionUtil.notifyAll("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(itemName));

            return null;
        }

        boolean coopOrder = byUsername != null && !byUsername.equalsIgnoreCase(localPlayerName);

        return new ParsedEntry(info.get(), item, correctedFill, claimableAmount, coopOrder);
    }

    private static @Nullable TransactionType.Side parseSide(Component name) {
        return name.getSiblings().stream()
                .filter(component -> component.getStyle().isBold())
                .map(Component::getString)
                .map(String::strip)
                .map(it -> switch (it) {
                    case "BUY" -> TransactionType.Side.BUY;
                    case "SELL" -> TransactionType.Side.SELL;
                    default -> null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves the filled numerator from lore to an exact item count.
     *
     * <p>The "Order/Offer amount" line is always an exact integer. The filled
     * line uses k/M shorthand and can round above the true total (e.g. "71.7k"
     * for a 71,680 order). Clamping to {@code totalAmount} is the correct fix.
     *
     * <p>When {@code fullyFilled} is {@code true} (the bold "100%!" marker was
     * present), the total is returned immediately without parsing.
     */
    private static int resolveScreenFill(@Nullable String filledStr, boolean fullyFilled, int totalAmount) {
        if (fullyFilled) return totalAmount;
        if (filledStr == null) return 0;
        return Math.min(Util.parseNumber(Util.removeFormatting(filledStr)), totalAmount);
    }

    /**
     * Resolves the claim line to a raw claimable unit count with no clamping applied.
     *
     * <h3>BUY orders</h3>
     * "You have N items to claim!" — N is in item units, parsed with k/M support.
     *
     * <h3>SELL offers</h3>
     * "You have X coins to claim!" — X is post-tax decimal coins, possibly k/M-abbreviated.
     * Unit count = {@code round(coins / (pricePerItem * (1 - tax)))}.
     *
     * <p>The caller is responsible for clamping and for using this value
     * to correct a k/M-rounded {@code filledAmount} before clamping.
     */
    private static int resolveRawClaimableUnits(TransactionType.Side side, @Nullable String claimStr, double pricePerItem) {
        if (claimStr == null) return 0;

        if (side == TransactionType.Side.BUY) {
            return Util.parseNumber(Util.removeFormatting(claimStr));
        }

        // SELL: coins → units back-calculation. Use parseNumber so k/M is handled.
        double coins = Util.parseNumber(Util.removeFormatting(claimStr));
        double tax = TaxContext.effectiveTaxPercent() / 100.0;

        double pricePostTax = pricePerItem * (1.0 - tax);

        return pricePostTax <= 0 ? 0 : (int) Math.round(coins / pricePostTax);
    }
}