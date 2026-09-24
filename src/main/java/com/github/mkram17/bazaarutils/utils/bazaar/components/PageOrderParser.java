package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
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
     * millisecond equality chases precision the data never had. Generous relative to the
     * scales these fields actually matter at (a 7-day order lifetime, hour-level warnings).
     */
    public static final long TIMESTAMP_GRACE_MS = 2 * 5_000;

    public static boolean isSameInstant(long a, long b) {
        return Math.abs(a - b) <= TIMESTAMP_GRACE_MS;
    }

    public static boolean isSameInstant(Optional<Long> a, Optional<Long> b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() == b.isEmpty();

        return isSameInstant(a.get(), b.get());
    }

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
     * Maximum units by which a k/M-abbreviated claimable line can under-report the
     * true value.
     */
    public static final int CLAIM_TRUNCATION_MAX = 100;

    /**
     * Hypixel's fixed order lifetime. Used to reverse a lore-observed {@code expiresAt}
     * into a {@code placedAt} estimate when synthesizing an untracked order.
     */
    public static final long ORDER_EXPIRY_MS = 7L * 24 * 3_600_000L;

    /**
     * Order/offer product name — "BUY Enchanted Coal" / "SELL Enchanted Coal".
     */
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
    private static final Pattern FILLAGE_CONTRIBUTION_HEADER_LIST_PATTERN = Pattern.compile("(?:Single vendor|Vendors|Single customer|Customers):");

    /**
     * "- 71,680x [MVP+] ZavenLoth 1h ago" — one contributor's exact, NEVER-abbreviated
     * unit count, plus a raw name/rank/time label. This amount is never k/M-shortened
     * even at a scale where the order's own "Filled:" line would be.
     */
    private static final Pattern FILLAGE_CONTRIBUTION_ENTRY_PATTERN = Pattern.compile("- (?<amount>[\\d,]+)x (?:\\[.*?] )?(?<username>[A-Za-z0-9_]{3,16}) (?<duration>[\\d.]+)(?<unit>[hms])");

    /** "+ 1 others" / "+ 15 others" — the vendor list was capped; the visible entries are a partial list, not the whole breakdown. */
    private static final Pattern FILLAGE_CONTRIBUTIONS_TRUNCATED_PATTERN = Pattern.compile("\\+ \\d+ others?");

    /** Hypixel shows at most this many raw contributor lines before switching to a "+N others" summary. */
    private static final int MAX_VISIBLE_CONTRIBUTORS = 7;

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
     * The per-contributor fill breakdown parsed from a "Vendors:"/"Single vendor:"
     * lore block, if the item had one at all.
     */
    public record FillBreakdown(List<Contribution> contributions, boolean truncated) {
        public static final FillBreakdown EMPTY = new FillBreakdown(List.of(), false);

        /** One contributor's exact unit count and a best-effort, cosmetic name/rank label — never load-bearing for {@link #exactFillTotal()}, which only ever needs the amounts. */
        public record Contribution(int amount, String label) {}

        private int rawSum() {
            return contributions.stream().mapToInt(Contribution::amount).sum();
        }

        /**
         * The order's exact filled total, if this breakdown proves it — present only
         * when the visible list is the COMPLETE list ({@link #truncated} is
         * {@code false}) and at least one contribution was parsed. A truncated list's
         * visible sum is a real lower bound, never an exact total — Hypixel gives no
         * way to recover the volume hidden behind a "+N others" trailer, so this
         * deliberately returns empty rather than a guess.
         */
        public OptionalInt exactFillTotal() {
            return !truncated && !contributions.isEmpty() ? OptionalInt.of(rawSum()) : OptionalInt.empty();
        }
    }

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
         * True when "Filled:" is exact — below Hypixel's k/M threshold, fully
         * filled, or corroborated by an untruncated contributor breakdown.
         */
        public boolean fillIsExact() {
            return filledAmount >= info.getVolume()
                    || filledAmount < FILL_TRUNCATION_THRESHOLD
                    || contributors.exactFillTotal().isPresent();
        }

        public boolean claimableIsTruncated() {
            return claimableAmount >= CLAIM_TRUNCATION_THRESHOLD;
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
         * <p>{@link #claimableIsTruncated()} — the claim line itself is k/M-abbreviated
         * and always an under-reported floor (see {@link #CLAIM_TRUNCATION_THRESHOLD}),
         * so a {@code screenClaimed} built from it can't be trusted at ANY delta size.
         * Whatever fill advanced this tick is newly-filled, not newly-claimed, and stays
         * implicit in the gap between claimed and (still-unresolved) claimable rather
         * than being credited to either.
         *
         * <p>Fill abbreviated — claimable itself is exact, but {@link #fillIsExact()} is
         * false — {@code screenClaimed} is subtracting an exact claimable from an
         * under-reported fill, so it can read a little low. That only matters when the
         * apparent change is small enough to plausibly BE that under-report
         * ({@code < CLAIM_TRUNCATION_MAX}); a jump past that bound is trusted,
         * abbreviated fill or not.
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
            long base = expiresAt.map(aLong -> aLong - ORDER_EXPIRY_MS)
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

        Matcher productMatcher = PRODUCT_PATTERN.matcher(name.getString());
        if (!productMatcher.find()) {
            Util.logMessage("Slot#%d → name pattern miss — name='%s'".formatted(item.slotIndex(), name.getString()));

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
            Util.logMessage("Slot#%d → pattern miss — lore=%s".formatted(item.slotIndex(), lore.toString()));

            return null;
        }

        var contributors = parseContributors(lore);

        int totalAmount;
        double price;
        int correctedFill;
        int claimableAmount;

        try {
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
                ? System.currentTimeMillis()
                : null;

        OrderStatus observedStatus = OrderStatus.observed(correctedFill, info.get().getVolume(), expired, origin.observedAt());

        return new ParsedEntry(info.get(), item, correctedFill, claimableAmount, attribution, observedStatus, Optional.ofNullable(finalExpiresAt), contributors);
    }

    private static FillBreakdown parseContributors(List<Component> lore) {
        var contributions = new ArrayList<FillBreakdown.Contribution>();
        boolean truncated = false;
        boolean inBlock = false;

        for (Component line : lore) {
            String plain = line.getString();

            if (!inBlock) {
                inBlock = FILLAGE_CONTRIBUTION_HEADER_LIST_PATTERN.matcher(plain).find();
                continue;
            }

            Matcher entryMatcher = FILLAGE_CONTRIBUTION_ENTRY_PATTERN.matcher(plain);
            boolean isEntry = entryMatcher.find();

            if (isEntry && contributions.size() < MAX_VISIBLE_CONTRIBUTORS) {
                int amount = Integer.parseInt(entryMatcher.group("amount").replace(",", "").trim());
                String label = entryMatcher.group("username").trim();

                contributions.add(new FillBreakdown.Contribution(amount, label));
                continue;
            }

            truncated = isEntry || FILLAGE_CONTRIBUTIONS_TRUNCATED_PATTERN.matcher(plain).find();
            break;
        }

        return new FillBreakdown(List.copyOf(contributions), truncated);
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
    private static int resolveRawClaimableUnits(TransactionType.Side side, ProfileKey key, @Nullable String claimStr, double pricePerItem) {
        if (claimStr == null) return 0;

        if (side == TransactionType.Side.BUY) {
            return Util.parseNumber(Util.removeFormatting(claimStr));
        }

        // SELL: coins → units back-calculation. Use parseNumber so k/M is handled.
        double coins = Util.parseNumber(Util.removeFormatting(claimStr));
        double tax = TaxContext.effectiveTaxPercent(key) / 100.0;

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