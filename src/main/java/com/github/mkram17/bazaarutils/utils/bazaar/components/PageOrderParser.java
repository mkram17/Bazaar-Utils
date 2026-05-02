package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
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
    private static final BazaarLogger LOG = BazaarLogger.of(PageOrderParser.class);

    /**
     * Maximum units by which a k/M-abbreviated fill line can under-report the true value.
     * Hypixel displays "71.7k" for any fill in [71,700 … 71,799], so the parsed integer
     * floors to the nearest 100. Consumers reasoning about fill precision use this bound.
     */
    public static final int FILL_TRUNCATION_MAX = 100;

    /**
     * Fill display is exact below this threshold — up to 1,099 the "Filled: X/Y" line is
     * plain digits. At 1,100 and above Hypixel abbreviates it with the same k/M shorthand
     * (and the same FILL_TRUNCATION_MAX granularity) used for claimable.
     */
    public static final int FILL_TRUNCATION_THRESHOLD = 1_000;

    /**
     * Claimable display is exact below this threshold. At or above 10k units Hypixel
     * abbreviates with the same k/M shorthand as fill, introducing up to
     * FILL_TRUNCATION_MAX − 1 units of undercount error in the raw claimable value.
     */
    public static final int CLAIM_TRUNCATION_THRESHOLD = 10_000;

    /**
     * Hypixel's fixed order lifetime. Used to reverse a lore-observed {@code expiresAt}
     * into a {@code placedAt} estimate when synthesizing an untracked order — see
     * {@link #resolvePlacedAt}.
     */
    public static final long ORDER_EXPIRY_MS = 7L * 24 * 3_600_000L;

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

    /**
     * "Expires in 10h!" or "Expires in 45m!" — present when ≤ 48 hours remain.
     * Order is still active and matching in the market.
     */
    private static final Pattern EXPIRES_IN_PATTERN = Pattern.compile("Expires in (?<amount>[\\d.]+)(?<unit>[hm])!");

    /**
     * "Expired!" — the order has lapsed. Hypixel removed its unfilled volume
     * from the market book. The slot remains for reclaim.
     */
    private static final Pattern EXPIRED_PATTERN = Pattern.compile("^Expired!$");

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
     * Pairs a resolved {@link OrderInfo} with its screen-observed fill, claim, and expiry state.
     *
     * <dl>
     *   <dt>{@code expired}</dt>
     *      <dd>{@code true} when {@code §8Expired!} was present in lore.</dd>
     *   <dt>{@code expiresAt}</dt>
     *      <dd>Epoch ms computed from {@code §8Expires in §eXh/m!} lore, or {@code null} if
     *       neither warning nor expired token was present (or if expired was present but
     *       no prior warning was seen — in that case it is stamped to {@code now}).</dd>
     * </dl>
     *
     * {@code expired} and a non-null {@code expiresAt} derived from the warning are
     * mutually exclusive — only one lore line appears at a time.
     */
    public record ParsedEntry(
            OrderInfo info,
            ItemInfo item,
            int filledAmount,
            int claimableAmount,
            boolean coopOrder,
            boolean expired,
            @Nullable Long expiresAt
    ) {
        /** Units filled and already claimed. Always {@code ≥ 0}. */
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

        if (NotificationType.SCREEN_PARSING.isEnabled()) {
            for (var entry : result) {
                PlayerLogger.debug(
                        "Slot#%d coop=%b exp=%b → %s %s %dx @ %.4f | filled=%d claimable=%d claimed=%d".formatted(
                                entry.item().slotIndex(),
                                entry.coopOrder(),
                                entry.expired(),
                                entry.info().getProductId(),
                                entry.info().getTransaction().getSide(),
                                entry.info().getVolume(),
                                entry.info().getPricePerItem(),
                                entry.filledAmount(),
                                entry.claimableAmount(),
                                entry.claimedAmount()),
                        NotificationType.SCREEN_PARSING, LOG);
            }
        }

        return result;
    }

    private static @Nullable ParsedEntry parseEntry(ItemInfo item, String localPlayerName) {
        var name = item.itemStack().getCustomName();
        if (name == null) {
            LOG.warn("Slot#{} → null custom name (unexpected after slot filter)", item.slotIndex());

            return null;
        }

        TransactionType.Side side = parseSide(name);
        if (side == null) {
            // All decoration slots are pre-filtered — null side here means Hypixel changed the format.
            LOG.warn("Slot#{} → null side (unexpected after slot filter) — name='{}'", item.slotIndex(), name.getString());

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
        boolean expired = false;
        @Nullable Long parsedExpiresAt = null;

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
            } else if (!expired && EXPIRED_PATTERN.matcher(plain).find()) {
                expired = true;
            } else if (parsedExpiresAt == null && (matcher = EXPIRES_IN_PATTERN.matcher(plain)).find()) {
                parsedExpiresAt = resolveExpiresAt(matcher.group("amount"), matcher.group("unit"), System.currentTimeMillis());
            }

            if (totalStr != null && filledStr != null && priceStr != null && claimStr != null) break;
        }

        if (totalStr == null || priceStr == null) {
            LOG.warn("Slot#{} → pattern miss — lore={}", item.slotIndex(), lore.toString());

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
            LOG.warn("Slot#{} → name resolution failed for '{}'", item.slotIndex(), itemName);
            PlayerLogger.send("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(itemName));

            return null;
        }

        boolean coopOrder = byUsername != null && !byUsername.equalsIgnoreCase(localPlayerName);

        Long finalExpiresAt = parsedExpiresAt != null
                ? parsedExpiresAt
                : expired
                  ? System.currentTimeMillis()
                  : null;

        return new ParsedEntry(info.get(), item, correctedFill, claimableAmount, coopOrder, expired, finalExpiresAt);
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

    private static long resolveExpiresAt(String amount, String unit, long now) {
        double value = Double.parseDouble(amount);

        long ms = "h".equals(unit)
                ? (long)(value * 3_600_000)
                : (long)(value * 60_000);

        return now + ms;
    }
}