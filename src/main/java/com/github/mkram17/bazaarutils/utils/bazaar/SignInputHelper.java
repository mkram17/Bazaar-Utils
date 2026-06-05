package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.config.util.api.conditions.AdvancedConfigurationMode;
import com.github.mkram17.bazaarutils.config.util.api.conditions.AllOf;
import com.github.mkram17.bazaarutils.config.util.api.conditions.ConfigCondition;
import com.github.mkram17.bazaarutils.data.HandledOrderAPI;
import com.github.mkram17.bazaarutils.config.util.api.conditions.MethodEquals;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.ProductPageLayout;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.sign.SignManager;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.profile.currency.CurrencyAPI;

import java.util.Optional;
import java.util.OptionalDouble;

interface SignInputState {
    @NotNull
    ItemInfo inputSign();
}

public abstract class SignInputHelper<T extends SignInputState> extends InputHelper<T> {
    public sealed interface ResolvedInput permits ResolvedInput.Value, ResolvedInput.ItemSearch {
        record Value(Number amount) implements ResolvedInput {
            public String format() {
                double d = amount.doubleValue();

                return d == (long) d
                        ? String.valueOf((long) d)
                        : String.valueOf(Util.truncateNum(d));
            }
        }

        record ItemSearch(String searchTerm) implements ResolvedInput {
            public String format() {
                return searchTerm;
            }
        }

        String format();
    }

    /**
     * Wraps the lazily-computed, memoized result of {@link #resolveInput}.
     *
     * <p>On first {@link #get()} call the memoized supplier fires {@code resolveInput} exactly
     * once and caches the result for the lifetime of this container load.
     */
    protected class WorkingValue {
        private final Supplier<ResolvedInput> resolver;

        WorkingValue(T state) {
            this.resolver = Suppliers.memoize(() -> resolveInput(state));
        }

        public ResolvedInput get() {
            return resolver.get();
        }
    }

    @Getter
    @NotNull
    protected BazaarSlots.BazaarSlot inputSignRef;

    @Nullable
    private WorkingValue workingValue;

    public SignInputHelper(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
        super(name);
        this.inputSignRef = inputSignRef;
    }

    protected Optional<ItemInfo> getInputSign(Container inventory) {
        return inputSignRef.query(inventory).first(inventory);
    }

    /**
     * Returns the live {@link WorkingValue} for this container load, creating it on first access.
     * All render and action paths read through here so {@link #resolveInput} is called at most once.
     */
    protected WorkingValue getWorkingValue(T state) {
        if (workingValue == null) workingValue = createWorkingValue(state);

        return workingValue;
    }

    /**
     * Factory method for subclasses that need custom step/clamp logic for scroll.
     * The default implementation returns a plain {@link WorkingValue} backed by
     * {@link #resolveInput}.
     */
    protected WorkingValue createWorkingValue(T state) {
        return new WorkingValue(state);
    }

    @Override
    protected void resetState() {
        workingValue = null;
        super.resetState();
    }

    @Override
    protected void handleAction(T state, Runnable resetState) {
        ContainerManager.clickSlot(state.inputSign().slotIndex(), 0);

        ResolvedInput input = getWorkingValue(state).get();

        SignManager.runOnNextSignOpen(event -> {
            SignManager.setSignText(input.format(), true);

            resetState.run();

            return Result.CONSUMED;
        });
    }

    protected abstract ResolvedInput resolveInput(T state);

    public abstract static class TransactionAmount extends SignInputHelper<TransactionAmount.TransactionState> {
        public record TransactionState(
                @NotNull
                Double purse,

                @NotNull
                ProductInfo productInfo,

                @NotNull
                ItemInfo productItem,

                @NotNull
                ItemInfo inputSign,

                @NotNull
                Inventory playerInventory,

                @NotNull
                Container container,

                @NotNull
                AbstractContainerScreen<ChestMenu> containerScreen
        ) implements SignInputState {
        }

        public enum AmountStrategy {
            FIXED,
            MAX,
        }

        /**
         * The prospect with which to resolve the output value.
         */
        protected abstract AmountStrategy getAmountStrategy();

        public static final class WhenFixedStrategy extends MethodEquals<TransactionAmount, AmountStrategy> {
            public WhenFixedStrategy() {
                super(TransactionAmount.class, TransactionAmount::getAmountStrategy, AmountStrategy.FIXED);
            }
        }

        @Override
        protected Optional<TransactionState> makeState(ContainerLoadedEvent event) {
            Container container = event.getContainer();

            Optional<ItemInfo> inputSign = getInputSign(container);
            if (inputSign.isEmpty()) return Optional.empty();

            Optional<ItemInfo> productItem = ScreenManager.getInstance()
                    .findBack(BazaarScreenType.PRODUCT_PAGE)
                    .flatMap(ProductPageLayout::getDisplayItem);

            if (productItem.isEmpty()) return Optional.empty();

            Optional<ProductInfo> productInfo = ScreenManager.getInstance()
                    .findBack(BazaarScreenType.PRODUCT_PAGE)
                    .flatMap(ProductPageLayout::getDisplayProductInfo);

            if (productInfo.isEmpty()) return Optional.empty();

            double purse = CurrencyAPI.INSTANCE.getPurse();

            Optional<Inventory> playerInventory = Optional.of(Minecraft.getInstance())
                    .flatMap(client -> Optional.ofNullable(client.player))
                    .map(LocalPlayer::getInventory);

            if (playerInventory.isEmpty()) return Optional.empty();

            return Optional.of(new TransactionState(purse, productInfo.get(), productItem.get(), inputSign.get(), playerInventory.get(), container, event.getScreen()));
        }

        public TransactionAmount(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
            super(name, inputSignRef);
        }

        @Override
        protected String getButtonItemStackSize(TransactionState state) {
            return getWorkingValue(state).get().format();
        }

        @Override
        protected ResolvedInput resolveInput(TransactionState state) {
            int amount = switch (getAmountStrategy()) {
                case MAX -> computeMaxValue(state);
                case FIXED -> computeFixedValue(state);
            };

            return new ResolvedInput.Value(amount);
        }

        protected abstract int computeFixedValue(TransactionState state);

        protected abstract int computeMaxValue(TransactionState state);
    }

    public abstract static class TransactionCost extends SignInputHelper<TransactionCost.TransactionState> {
        public record TransactionState(
                @NotNull
                ProfileKey key,

                @NotNull
                ProductInfo productInfo,

                @NotNull
                ItemInfo inputSign,

                @NotNull
                Container container,

                @NotNull
                AbstractContainerScreen<ChestMenu> screen
        ) implements SignInputState {
        }

        /**
         * The prospect with which to resolve the output value.
         */
        protected abstract PricingPosition getPricingPosition();

        public static final class WhenCompetitivePosition extends MethodEquals<TransactionCost, PricingPosition> {
            public WhenCompetitivePosition() {
                super(TransactionCost.class, TransactionCost::getPricingPosition, PricingPosition.COMPETITIVE);
            }

            public static final class AndAdvancedMode extends AllOf {
                @Override
                @SuppressWarnings("unchecked")
                protected Class<? extends ConfigCondition>[] conditions() {
                    return new Class[] {
                            WhenCompetitivePosition.class,
                            AdvancedConfigurationMode.class
                    };
                }
            }
        }

        /** Whether this button treats own top-of-book orders as external competitors when computing COMPETITIVE price. */
        protected abstract boolean isSelfOutbid();

        /**
         * Assumed market price per item when the Bazaar book has no orders.
         * Treated as a hypothetical top-of-book and passed through
         * {@link PricingPosition#adjust} — the actual sign value is offset and
         * clamped to the bid/ask window anchored at this price.
         * Clamped to at least {@link PriceInfo#MINIMUM_PRICE} at runtime.
         */
        protected abstract double getEmptyMarketPrice();

        protected Optional<ProductInfo> getItemProductInfo(ItemInfo inputSign) {
            return ScreenManager.getInstance()
                    .findBack(BazaarScreenType.PRODUCT_PAGE)
                    .flatMap(ProductPageLayout::getDisplayProductInfo);
        }

        @Override
        protected Optional<TransactionState> makeState(ContainerLoadedEvent event) {
            Container container = event.getContainer();

            ProfileKey key = ProfileKey.requireProfile(this.name); if (key == null) return Optional.empty();

            Optional<ItemInfo> inputSign = getInputSign(container);
            if (inputSign.isEmpty()) return Optional.empty();

            Optional<ProductInfo> productInfo = getItemProductInfo(inputSign.get());
            if (productInfo.isEmpty()) return Optional.empty();

            return Optional.of(new TransactionState(key, productInfo.get(), inputSign.get(), container, event.getScreen()));
        }

        public TransactionCost(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
            super(name, inputSignRef);
        }

        @Override
        protected String getButtonItemStackSize(TransactionState state) {
            return getWorkingValue(state).get().format();
        }

        @Override
        protected ResolvedInput resolveInput(TransactionState state) {
            var storage = UserOrdersStorage.orders(state.key());

            OptionalDouble price = PriceInfo.priceForPosition(
                    state.productInfo().getProductId(),
                    getTransactionType(),
                    getPricingPosition(),
                    storage,
                    isSelfOutbid());

            double resolved = price.orElseGet(() -> {
                double market = Math.max(PriceInfo.MINIMUM_PRICE, getEmptyMarketPrice());
                double fallback = getPricingPosition().adjust(market, getTransactionType());
                Util.logMessage("%s.resolveInput: book empty for %s %s @ %s — using fallback price %f".formatted(name, state.productInfo().getProductId(), getTransactionType(), getPricingPosition(), fallback));

                return fallback;
            });

            return new ResolvedInput.Value(resolved);
        }
    }

    public abstract static class TransactionFlip extends TransactionCost {
        public TransactionFlip(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
            super(name, inputSignRef);
        }

        @Override
        protected Optional<ProductInfo> getItemProductInfo(ItemInfo inputSign) {
            var result = HandledOrderAPI.getForOptions()
                    .flatMap(OrderInfo::of)
                    .map((info) -> (ProductInfo) info);

            if (result.isEmpty()) PlayerActionUtil.notifyAll("Flip helper found no current order selected in data layer — price will be unavailable", NotificationType.FEATURE);

            return result;
        }
    }
}