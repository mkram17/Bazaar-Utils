package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderAttribution;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
     * How much two independently-derived timestamps (an expiresAt re-parsed from lore, or
     * a placedAt reversed from it) may differ and still count as the same moment for
     * change-detection purposes. The lore only gives hour/minute-grained countdowns, so
     * anything derived from it inherits that slack — comparing these fields for exact
     * millisecond equality chases precision the data never had.
     */
    public static final long TIMESTAMP_GRACE_MS = 2 * 5_000L;

    public static boolean isSameInstant(long a, long b) {
        return Math.abs(a - b) <= TIMESTAMP_GRACE_MS;
    }

    public static boolean isSameInstant(Optional<Long> a, Optional<Long> b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() == b.isEmpty();

        return isSameInstant(a.get(), b.get());
    }

    /**
     * Maximum units by which an abbreviated claimable line can under-report the true value.
     */
    public static final int CLAIM_TRUNCATION_MAX = Order.FILL_TRUNCATION_MAX;

    /** Order/offer product name — "BUY Enchanted Coal" / "SELL Enchanted Coal". */
    private static final Pattern PRODUCT_PATTERN = Pattern.compile("(?<side>BUY|SELL)\\s+(?<product>.+)");

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
    private static final Pattern FILLED_PATTERN = Pattern.compile("Filled: (?<filled>[\\d,.kKmMbB]+)/");

    /**
     * Detects the bold "100%!" completion marker.
     * When present, {@link #resolveScreenFill} short-circuits to {@code totalAmount}.
     */
    private static final Pattern FULL_FILL_PATTERN = Pattern.compile("100%!");

    /**
     * "Expires in 10h!", "Expires in 45m!", or "Expires in 30s!" — present when ≤ 48 hours remain.
     * Order is still active and matching in the market.
     */
    private static final Pattern EXPIRES_IN_PATTERN = Pattern.compile("Expires in (?<amount>[\\d.]+)(?<unit>[hms])!");

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
    private static final Pattern COOP_AUTHOR_PATTERN = Pattern.compile("By: (?:\\[.*?] )?(?<username>[A-Za-z0-9_]{3,16})");

    /**
     * "Vendors:" or "Single vendor:" — introduces the per-contributor fill breakdown
     * for a BUY order (who SOLD into it). The SELL-side is equivalent ("Customers:" /
     * "Single customer:").
     */
    private static final Pattern FILL_CONTRIBUTION_HEADER_PATTERN = Pattern.compile("(?:Single vendor|Vendors|Single customer|Customers):");

    /**
     * "- 71,680x [MVP+] ZavenLoth 1h ago" / "2d ago" — one contributor's exact,
     * never-abbreviated unit count, plus a name/rank/time label.
     */
    private static final Pattern FILL_CONTRIBUTION_ENTRY_PATTERN = Pattern.compile("- (?<amount>[\\d,]+)x (?:\\[.*?] )?(?<username>[A-Za-z0-9_]{3,16}) (?<duration>[\\d.]+)(?<unit>[dhms])");

    /** "+ 1 others" / "+ 15 others" — the vendor list was capped; the visible entries are a partial list, not the whole breakdown. */
    private static final Pattern FILL_CONTRIBUTIONS_TRUNCATED_PATTERN = Pattern.compile("\\+ \\d+ others?");

    /** Hypixel shows at most this many raw contributor lines before switching to a "+N others" summary. */
    private static final int MAX_VISIBLE_CONTRIBUTORS = 7;

    /**
     * BUY order: "You have 1,429 items to claim!"
     * Value is in item units; may use k/M/B shorthand.
     */
    private static final Pattern BUY_CLAIM_PATTERN = Pattern.compile("You have (?<claimable>[\\d,.kKmMbB]+) items to claim!");

    /**
     * SELL offer: "You have 74.4 coins to claim!"
     * Value is post-tax coins; unit count is back-calculated in {@link #resolveRawClaimableUnits}.
     */
    private static final Pattern SELL_CLAIM_PATTERN = Pattern.compile("You have (?<claimable>[\\d,.kKmMbB]+) coins to claim!");

    /**
     * The per-contributor fill breakdown parsed from a "Vendors:"/"Single vendor:"
     * (or "Customers:"/"Single customer:") lore block, if the item had one at all.
     */
    public record FillBreakdown(List<Contribution> contributions, boolean truncated) {
        public static final FillBreakdown EMPTY = new FillBreakdown(List.of(), false);

        /** One contributor's exact unit count and a best-effort, cosmetic name/rank label. */
        public record Contribution(int amount, String label) {}

        private int rawSum() {
            return contributions.stream().mapToInt(Contribution::amount).sum();
        }

        /**
         * The order's exact filled total, if this breakdown proves it — present only
         * when the visible list is complete ({@link #truncated} is {@code false}) and
         * at least one contribution was parsed.
         */
        public OptionalInt exactFillTotal() {
            return !truncated && !contributions.isEmpty() ? OptionalInt.of(rawSum()) : OptionalInt.empty();
        }
    }

    /**
     * Pairs a resolved {@link OrderInfo} with its screen-observed fill, claim, and expiry state.
     *
     * <dl>
     *   <dt>{@code observedStatus}</dt>
     *      <dd>Observed {@link OrderStatus}; {@link #expired()} is {@code true} when
     *       {@code Expired!} was present in lore.</dd>
     *   <dt>{@code expiresAt}</dt>
     *      <dd>Epoch ms computed from {@code Expires in Xh/m/s!} lore, stamped to
     *       {@code origin.observedAt()} when {@code Expired!} was seen without a prior
     *       warning, or empty when neither token was present.</dd>
     * </dl>
     */
    public record ParsedEntry(
            OrderInfo info,
            ItemInfo item,
            int filledAmount,
            int claimableAmount,
            boolean filledAbbreviated,
            boolean claimableAbbreviated,
            OrderAttribution attribution,
            OrderStatus observedStatus,
            Optional<Long> expiresAt,
            FillBreakdown contributors
    ) {
        private int screenFill(int originalAmount) {
            return Math.min(filledAmount, originalAmount);
        }

        private int screenClaimable(int originalAmount) {
            return Math.clamp(claimableAmount, 0, screenFill(originalAmount));
        }

        /** Filled − claimable, on the SCREEN's own basis. */
        private int screenClaimed(int originalAmount) {
            return Math.max(0, screenFill(originalAmount) - screenClaimable(originalAmount));
        }

        /**
         * True when "Filled:" is exact — not k/M/B-abbreviated, fully filled, or
         * corroborated by an untruncated contributor breakdown.
         */
        public boolean fillIsExact() {
            return !filledAbbreviated
                    || filledAmount >= info.getVolume()
                    || contributors.exactFillTotal().isPresent();
        }

        /** True when the raw claimable lore line used k/M/B abbreviation (items for BUY, coins for SELL). */
        public boolean claimableIsTruncated() {
            return claimableAbbreviated;
        }

        public boolean claimableIsZero() {
            return claimableAmount == 0;
        }

        /**
         * Screen step then max-against-stored, capped at originalAmount. A screen
         * reading at full short-circuits: we know it's complete.
         */
        public int getFilled(int previous, int originalAmount) {
            int screenFill = screenFill(originalAmount);

            return screenFill >= originalAmount
                    ? originalAmount
                    : Math.max(previous, screenFill);
        }

        /**
         * Reconciles the claimed total. Two independent reasons to freeze at
         * {@code previousClaimed} instead of trusting this tick's screen reading:
         *
         * <p>{@link #claimableIsTruncated()} — the claim line itself is k/M/B-abbreviated
         * and always an under-reported floor, so a {@code screenClaimed} built from it
         * can't be trusted at any delta size.
         *
         * <p>Fill abbreviated — claimable itself is exact, but {@link #fillIsExact()} is
         * false — {@code screenClaimed} is subtracting an exact claimable from an
         * under-reported fill, so small differences ({@code < CLAIM_TRUNCATION_MAX}) are
         * absorbed while jumps past that bound are trusted.
         */
        public int getClaimed(int previousClaimed, int resolvedFill, int originalAmount) {
            if (claimableIsTruncated()) {
                return Math.min(previousClaimed, resolvedFill);
            }

            if (claimableIsZero()) {
                return resolvedFill;
            }

            int screenClaimed = screenClaimed(originalAmount);

            boolean fillAbbreviated = !fillIsExact();
            boolean withinTrustBand = Math.abs(screenClaimed - previousClaimed) < CLAIM_TRUNCATION_MAX;

            if (fillAbbreviated && withinTrustBand) {
                return Math.min(previousClaimed, resolvedFill);
            }

            int reconciled = Math.max(previousClaimed, screenClaimed);

            return Math.min(reconciled, resolvedFill);
        }

        public long resolvePlacedAt(BazaarDataOrigin.OrdersScreen origin, long staggerOffset) {
            long base = expiresAt.map(aLong -> aLong - Order.ORDER_EXPIRY_MS)
                    .orElseGet(origin::observedAt);

            return base - staggerOffset;
        }

        public boolean expired() {
            return observedStatus instanceof OrderStatus.Expired;
        }
    }

    private PageOrderParser() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public static List<ParsedEntry> parse(List<ItemInfo> items, int containerSize, ProfileKey key, String localPlayerName, boolean isKnownCoop, BazaarDataOrigin.OrdersScreen origin) {
        var result = items.stream()
                .filter(item -> !item.isEmpty())
                .filter(item -> OrdersPageLayout.isOrderSlot(item.slotIndex(), containerSize))
                .map(item -> parseEntry(item, key, localPlayerName, isKnownCoop, origin))
                .filter(Objects::nonNull)
                .toList();

        if (NotificationType.GUI.isEnabled()) {
            for (var entry : result) {
                PlayerActionUtil.notifyAll(
                        "Slot#%d attr=%s exp=%b → %s %s %dx @ %.4f | filled=%d claimable=%d claimed=%d".formatted(
                                entry.item().slotIndex(),
                                entry.attribution(),
                                entry.expired(),
                                entry.info().getProductId(),
                                entry.info().getTransaction().getSide(),
                                entry.info().getVolume(),
                                entry.info().getPricePerItem(),
                                entry.filledAmount(),
                                entry.claimableAmount(),
                                entry.getClaimed(0, entry.filledAmount(), entry.info().getVolume())) +
                                " | contributors=%d(truncated=%b)".formatted(entry.contributors().contributions().size(), entry.contributors().truncated()),
                        NotificationType.GUI);
            }
        }

        return result;
    }

    private static @Nullable ParsedEntry parseEntry(ItemInfo item, ProfileKey key, String localPlayerName, boolean isKnownCoop, BazaarDataOrigin.OrdersScreen origin) {
        var name = item.itemStack().getCustomName();
        if (name == null) {
            Util.logMessage("Slot#%d → null custom name (unexpected after slot filter)".formatted(item.slotIndex()));

            return null;
        }

        String plainName = Util.stripFormatCodes(name.getString());
        Matcher productMatcher = PRODUCT_PATTERN.matcher(plainName);
        if (!productMatcher.find()) {
            Util.logMessage("Slot#%d → name pattern miss — name='%s'".formatted(item.slotIndex(), plainName));

            return null;
        }

        TransactionType.Side side = "BUY".equals(productMatcher.group("side")) ? TransactionType.Side.BUY : TransactionType.Side.SELL;
        String productName = productMatcher.group("product").strip();

        List<Component> lore = LoreParser.lines(item.itemStack());

        String priceStr = null;
        String totalStr = null;
        String claimStr = null;
        String filledStr = null;
        String byUsername = null;
        boolean fullyFilled = false;
        boolean expired = false;
        @Nullable Long parsedExpiresAt = null;

        Pattern claimPattern = side == TransactionType.Side.BUY
                ? BUY_CLAIM_PATTERN : SELL_CLAIM_PATTERN;

        for (Component line : lore) {
            String plain = Util.stripFormatCodes(line.getString()).trim();
            Matcher matcher;

            if (totalStr == null && (matcher = AMOUNT_PATTERN.matcher(plain)).find()) {
                totalStr = matcher.group("amount");
            } else if (filledStr == null && (matcher = FILLED_PATTERN.matcher(plain)).find()) {
                filledStr = matcher.group("filled");
                fullyFilled = FULL_FILL_PATTERN.matcher(plain).find();
            } else if (priceStr == null && (matcher = PRICE_PATTERN.matcher(plain)).find()) {
                priceStr = matcher.group("price");
            } else if (claimStr == null && (matcher = claimPattern.matcher(plain)).find()) {
                claimStr = matcher.group("claimable");
            } else if (byUsername == null && (matcher = COOP_AUTHOR_PATTERN.matcher(plain)).find()) {
                byUsername = matcher.group("username");
            } else if (!expired && EXPIRED_PATTERN.matcher(plain).find()) {
                expired = true;
            } else if (parsedExpiresAt == null && (matcher = EXPIRES_IN_PATTERN.matcher(plain)).find()) {
                parsedExpiresAt = resolveExpiresAt(matcher.group("amount"), matcher.group("unit"), origin.observedAt());
            }
        }

        if (totalStr == null || priceStr == null) {
            Util.logMessage("Slot#%d → pattern miss — lore=%s".formatted(item.slotIndex(), lore.toString()));

            return null;
        }

        int totalAmount;
        double price;
        int correctedFill;
        int claimableAmount;
        FillBreakdown contributors;

        try {
            contributors = parseContributors(lore);
            totalAmount = Integer.parseInt(totalStr.replace(",", "").trim());
            price = Double.parseDouble(priceStr.replace(",", "").trim());

            int filledAmount = resolveScreenFill(filledStr, fullyFilled, totalAmount);
            int rawClaimable = resolveRawClaimableUnits(side, key, claimStr, price);
            int clampedClaimable = Math.min(rawClaimable, totalAmount);

            correctedFill = Math.clamp(filledAmount, clampedClaimable, totalAmount);

            var exactFill = contributors.exactFillTotal();
            if (exactFill.isPresent()) {
                int vendorDerivedFill = Math.clamp(exactFill.getAsInt(), clampedClaimable, totalAmount);

                if (vendorDerivedFill != correctedFill && NotificationType.GUI.isEnabled()) {
                    PlayerActionUtil.notifyAll("Slot#%d — vendor breakdown corrected fill %d → %d (%d contributors, exact)".formatted(
                            item.slotIndex(), correctedFill, vendorDerivedFill, contributors.contributions().size()), NotificationType.GUI);
                }

                correctedFill = vendorDerivedFill;
            }

            claimableAmount = Math.clamp(rawClaimable, 0, correctedFill);
        } catch (Exception exception) {
            Util.logError("Slot#%d → arithmetic failed — total='%s' price='%s' filled='%s' claim='%s'".formatted(item.slotIndex(), totalStr, priceStr, filledStr, claimStr), exception);

            return null;
        }

        Optional<OrderInfo> info = OrderInfo.of(productName, side, price, totalAmount, true);
        if (info.isEmpty()) {
            PlayerActionUtil.notifyAll("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(productName));

            return null;
        }

        OrderAttribution attribution = OrderAttribution.fromByLine(byUsername, localPlayerName, isKnownCoop);

        Long finalExpiresAt = parsedExpiresAt != null
                ? parsedExpiresAt
                : expired
                ? origin.observedAt()
                : null;

        OrderStatus observedStatus = OrderStatus.observed(correctedFill, info.get().getVolume(), expired, origin.observedAt());

        boolean filledAbbreviated = filledStr != null && !fullyFilled && hasAbbreviationSuffix(filledStr);
        boolean claimableAbbreviated = claimStr != null && hasAbbreviationSuffix(claimStr);

        return new ParsedEntry(
                info.get(),
                item,
                correctedFill,
                claimableAmount,
                filledAbbreviated,
                claimableAbbreviated,
                attribution,
                observedStatus,
                Optional.ofNullable(finalExpiresAt),
                contributors
        );
    }

    private static boolean hasAbbreviationSuffix(String value) {
        if (value.isEmpty()) return false;
        char last = Character.toLowerCase(value.charAt(value.length() - 1));
        return last == 'k' || last == 'm' || last == 'b';
    }

    private static FillBreakdown parseContributors(List<Component> lore) {
        var contributions = new ArrayList<FillBreakdown.Contribution>();
        boolean truncated = false;
        boolean inBlock = false;

        for (Component line : lore) {
            String plain = Util.stripFormatCodes(line.getString()).trim();

            if (!inBlock) {
                inBlock = FILL_CONTRIBUTION_HEADER_PATTERN.matcher(plain).find();
                continue;
            }

            Matcher entryMatcher = FILL_CONTRIBUTION_ENTRY_PATTERN.matcher(plain);
            boolean isEntry = entryMatcher.find();

            if (isEntry && contributions.size() < MAX_VISIBLE_CONTRIBUTORS) {
                int amount = Integer.parseInt(entryMatcher.group("amount").replace(",", "").trim());
                String label = entryMatcher.group("username").trim();

                contributions.add(new FillBreakdown.Contribution(amount, label));
                continue;
            }

            // Fail safe: an unrecognized "- " bullet inside the block (e.g. an unexpected
            // time unit or formatting change) marks the breakdown truncated so a partial
            // sum can never overwrite correctedFill downward.
            truncated = isEntry
                    || FILL_CONTRIBUTIONS_TRUNCATED_PATTERN.matcher(plain).find()
                    || plain.startsWith("- ");
            break;
        }

        return new FillBreakdown(List.copyOf(contributions), truncated);
    }

    /**
     * Resolves the filled numerator from lore to an item count, clamped to {@code totalAmount}.
     * When {@code fullyFilled} is {@code true} ("100%!"), returns {@code totalAmount} directly.
     */
    private static int resolveScreenFill(@Nullable String filledStr, boolean fullyFilled, int totalAmount) {
        if (fullyFilled) return totalAmount;
        if (filledStr == null) return 0;

        long parsed = Math.round(Util.parseDoubleNumber(Util.removeFormatting(filledStr)));

        return (int) Math.clamp(parsed, 0L, totalAmount);
    }

    /**
     * Resolves the claim line to a raw claimable unit count with no clamping applied.
     *
     * <h3>BUY orders</h3>
     * "You have N items to claim!" — N is in item units, parsed with k/M/B support.
     *
     * <h3>SELL offers</h3>
     * "You have X coins to claim!" — X is post-tax decimal coins, possibly k/M/B-abbreviated.
     * Unit count = {@code round(coins / (pricePerItem * (1 - tax)))}.
     */
    private static int resolveRawClaimableUnits(TransactionType.Side side, ProfileKey key, @Nullable String claimStr, double pricePerItem) {
        if (claimStr == null) return 0;

        double parsed = Util.parseDoubleNumber(Util.removeFormatting(claimStr));
        if (side == TransactionType.Side.BUY) {
            return (int) Math.clamp(Math.round(parsed), 0L, Integer.MAX_VALUE);
        }

        double tax = TaxContext.effectiveTaxPercent(key) / 100.0;
        double pricePostTax = pricePerItem * (1.0 - tax);

        return pricePostTax <= 0.0 ? 0 : (int) Math.clamp(Math.round(parsed / pricePostTax), 0L, Integer.MAX_VALUE);
    }

    private static long resolveExpiresAt(String amount, String unit, long observedAt) {
        double value = Double.parseDouble(amount);

        long unitMs = switch (unit) {
            case "h" -> 3_600_000L;
            case "m" -> 60_000L;
            case "s" -> 1_000L;
            default -> throw new IllegalArgumentException("Unknown expiry unit: " + unit);
        };

        return observedAt + (long) (value * unitMs);
    }
}
