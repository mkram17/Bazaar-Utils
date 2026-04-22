package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.data.CurrentOrderData;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.ItemPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.TransactionPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.sign.SignManager;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.profile.CurrencyAPI;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

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

    @Getter
    @NotNull
    protected BazaarSlots.BazaarSlot inputSignRef;

    public SignInputHelper(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
        super(name);

        this.inputSignRef = inputSignRef;
    }

    protected Optional<ItemInfo> getInputSign(Container inventory) {
        return inputSignRef.query(inventory).first(inventory);
    }

    @Override
    protected void handleAction(T state) {
        ContainerManager.clickSlot(state.inputSign().slotIndex(), 0);

        ResolvedInput input = resolveInput(state);

        SignManager.runOnNextSignOpen(event -> SignManager.setSignText(input.format(), true));
    }

    protected abstract ResolvedInput resolveInput(T state);

    public abstract static class TransactionAmount extends SignInputHelper<TransactionAmount.TransactionState> {
        private static final Pattern PURSE_PATTERN = Pattern.compile("(Purse|Piggy): (?<purse>[0-9,.]+)");

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
                ContainerScreen containerScreen
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

        @Override
        protected Optional<TransactionState> makeState(ChestLoadedEvent event) {
            Optional<ContainerScreen> container = ScreenManager.getInstance()
                    .current()
                    .flatMap(context -> context.as(ContainerScreen.class));

            Optional<Container> inventory = container
                    .map(ContainerScreen::getMenu)
                    .map(ChestMenu::getContainer);

            if (container.isEmpty() || inventory.isEmpty()) return Optional.empty();

            Optional<ItemInfo> inputSign = inventory.flatMap(this::getInputSign);

            if (inputSign.isEmpty()) return Optional.empty();

            Optional<ItemInfo> productItem = ScreenManager.getInstance()
                    .findBack(BazaarScreenType.ITEM_PAGE)
                    .flatMap(ItemPageLayout::getDisplayItem);

            if (productItem.isEmpty()) return Optional.empty();

            Optional<ProductInfo> productInfo = ScreenManager.getInstance()
                    .findBack(BazaarScreenType.ITEM_PAGE)
                    .flatMap(ItemPageLayout::getDisplayProductInfo);

            if (productInfo.isEmpty()) return Optional.empty();

            Double purse = CurrencyAPI.INSTANCE.getPurse();

            Optional<Inventory> playerInventory = Optional.ofNullable(Minecraft.getInstance())
                    .flatMap(client -> Optional.ofNullable(client.player))
                    .map(LocalPlayer::getInventory);

            if (playerInventory.isEmpty()) return Optional.empty();

            return Optional.of(new TransactionState(purse, productInfo.get(), productItem.get(), inputSign.get(), playerInventory.get(), container.get()));
        }

        public TransactionAmount(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
            super(name, inputSignRef);
        }

        @Override
        protected String getButtonItemStackSize(TransactionState state) {
            ResolvedInput input = resolveInput(state);

            return input.format();
        }

        @Override
        protected ResolvedInput resolveInput(TransactionState state) {
            OptionalDouble price = PriceInfo.marketPrice(state.productInfo().getProductId(), getTransactionType());

            if (price.isEmpty()) {
                Util.logMessage("Could not retrieve relevant item pricing for " + name + "'s resolved value.");

                return new ResolvedInput.Value(0);
            }

            int amount = switch (getAmountStrategy()) {
                case MAX -> computeMaxValue(state);
                case FIXED -> computeFixedValue(state);
            };

            return new ResolvedInput.Value(amount);
        }

        protected abstract int computeFixedValue(TransactionState state);

        protected int computeMaxValue(TransactionState state) {
            return switch (getTransactionType().getMethod()) {
                case INSTANT -> {
                    if (getTransactionType().isBuy()) {
                        yield Optional.of(state.containerScreen())
                            .map(ContainerScreen::getMenu)
                            .map(ChestMenu::getContainer)
                            .map(inventory -> SlotLookup.getInventoryItem(inventory, BazaarSlots.INSTANT_BUY.INPUT_FILLING_AMOUNT.slot))
                            .map(ItemInfo::itemStack)
                            .flatMap(TransactionPageLayout::findOptionAmount)
                            .map(value -> (int) Math.floor(value))
                            .orElse((int) state.playerInventory()
                                    .getNonEquipmentItems()
                                    .stream()
                                    .filter(ItemStack::isEmpty)
                                    .count()
                            );
                    }
                    // Should be impossible to reach, as there is no sign to input a custom amount on items to instant sell.
                    // TODO: consider refactors needed for this case not to exist
                    yield 0;
                }
                case ORDER -> {
                    if (getTransactionType().isBuy()) {
                        int amountCanAfford = (int) Math.min(state.purse() / PriceInfo.priceForPosition(state.productInfo().getProductId(), getTransactionType(), PricingPosition.COMPETITIVE).getAsDouble(), 71680);

                        yield TransactionPageLayout.findBuyOrderAmountLimit(state.inputSign().itemStack())
                                .map(limit -> Math.min(amountCanAfford, limit))
                                .orElse(amountCanAfford);
                    }
                    yield state.playerInventory().getNonEquipmentItems().stream()
                            .filter(stack -> !stack.isEmpty())
                            .filter(stack -> Optional.ofNullable(stack.getCustomName())
                                    .map(Component::getString)
                                    .flatMap(ProductInfo::fromDisplayName)
                                    .map(info -> info.getProductId().equals(state.productInfo().getProductId()))
                                    .orElse(false))
                            .mapToInt(ItemStack::getCount)
                            .sum();
                }
            };
        }
    }

    public abstract static class TransactionCost extends SignInputHelper<TransactionCost.TransactionState> {
        public record TransactionState(
                @NotNull
                ProductInfo productInfo,

                @NotNull
                ItemInfo inputSign,

                @NotNull
                ContainerScreen containerScreen
        ) implements SignInputState {
        }

        /**
         * The prospect with which to resolve the output value.
         */
        protected abstract PricingPosition getPricingPosition();

        protected Optional<ProductInfo> getItemProductId(ItemInfo inputSign) {
            return ScreenManager.getInstance()
                    .findBack(BazaarScreenType.ITEM_PAGE)
                    .flatMap(ItemPageLayout::getDisplayProductInfo);
        }

        @Override
        protected Optional<TransactionState> makeState(ChestLoadedEvent event) {
            Optional<ContainerScreen> container = ScreenManager.getInstance()
                    .current()
                    .flatMap(context -> context.as(ContainerScreen.class));

            Optional<Container> inventory = container
                    .map(ContainerScreen::getMenu)
                    .map(ChestMenu::getContainer);

            if (container.isEmpty() || inventory.isEmpty()) return Optional.empty();

            Optional<ItemInfo> inputSign = inventory.flatMap(this::getInputSign);

            if (inputSign.isEmpty()) return Optional.empty();

            Optional<ProductInfo> productInfo = getItemProductId(inputSign.get());

            if (productInfo.isEmpty()) return Optional.empty();

            return Optional.of(new TransactionState(productInfo.get(), inputSign.get(), container.get()));
        }

        public TransactionCost(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
            super(name, inputSignRef);
        }

        @Override
        protected String getButtonItemStackSize(TransactionState state) {
            ResolvedInput input = resolveInput(state);

            return input.format();
        }

        @Override
        protected ResolvedInput resolveInput(TransactionState state) {
            var storage = UserOrdersStorage.INSTANCE.get();
            List<Order> userOrders = storage != null ? storage : List.of();

            OptionalDouble price = PriceInfo.priceForPosition(
                    state.productInfo().getProductId(),
                    getTransactionType(),
                    getPricingPosition(),
                    userOrders);

            if (price.isEmpty()) {
                Util.logMessage("Could not retrieve relevant item pricing for " + name + "'s resolved value.");

                return new ResolvedInput.Value(0);
            }

            return new ResolvedInput.Value(price.getAsDouble());
        }
    }

    public abstract static class TransactionFlip extends TransactionCost {
        public TransactionFlip(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
            super(name, inputSignRef);
        }

        @Override
        protected Optional<ProductInfo> getItemProductId(ItemInfo inputSign) {
            return CurrentOrderData.getForOptions()
                    .flatMap(OrderInfo::of)
                    .map((info) -> info);
        }
    }
}