package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.utils.storage.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.sign.SignManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
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

    protected Optional<ItemInfo> getInputSign(Inventory inventory) {
        int slot = inputSignRef.resolve(inventory);

        return inputSignRef.query(inventory).first(inventory).map(stack -> new ItemInfo(slot, stack));
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
                String productId,

                @NotNull
                ItemInfo productItem,

                @NotNull
                ItemInfo inputSign,

                @NotNull
                PlayerInventory playerInventory,

                @NotNull
                GenericContainerScreen containerScreen
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
            Optional<GenericContainerScreen> container = ScreenManager.getInstance()
                    .current()
                    .flatMap(context -> context.as(GenericContainerScreen.class));

            Optional<Inventory> inventory = container
                    .map(GenericContainerScreen::getScreenHandler)
                    .map(GenericContainerScreenHandler::getInventory);

            if (container.isEmpty() || inventory.isEmpty()) return Optional.empty();

            Optional<ItemInfo> inputSign = inventory.flatMap(this::getInputSign);

            if (inputSign.isEmpty()) return Optional.empty();

            Optional<ItemInfo> productItem = ScreenManager.getInstance()
                    .findBack(BazaarScreens.ITEM_PAGE)
                    .flatMap(BazaarScreenHandler::getDisplayItem);

            if (productItem.isEmpty()) return Optional.empty();

            Optional<String> productId = ScreenManager.getInstance()
                    .findBack(BazaarScreens.ITEM_PAGE)
                    .flatMap(BazaarScreenHandler::getDisplayProductId);

            if (productId.isEmpty()) return Optional.empty();

            Optional<Double> purse = Optional.ofNullable(MinecraftClient.getInstance())
                    .flatMap(client -> Optional.ofNullable(client.world))
                    .flatMap(world -> Optional.ofNullable(world.getScoreboard()))
                    .flatMap(scoreboard -> {
                        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

                        if (objective == null) {
                            return Optional.empty();
                        }

                        ObjectArrayList<String> scoreboardLines = new ObjectArrayList<>();

                        for (ScoreHolder scoreHolder : scoreboard.getKnownScoreHolders()) {
                            if (scoreboard.getScoreHolderObjectives(scoreHolder).containsKey(objective)) {
                                Team team = scoreboard.getScoreHolderTeam(scoreHolder.getNameForScoreboard());

                                if (team != null) {
                                    String line = team.getPrefix().getString() + team.getSuffix().getString();

                                    if (!line.trim().isEmpty()) {
                                        scoreboardLines.add(Formatting.strip(line));
                                    }
                                }
                            }
                        }

                        return Optional.of(scoreboardLines);
                    })
                    .flatMap(lines -> {
                        for (String line : lines) {
                            if (line.contains("Purse:") || line.contains("Piggy:")) {
                                Matcher matcher = PURSE_PATTERN.matcher(line);

                                if (matcher.find()) {
                                    try {
                                        return Optional.of(Double.parseDouble(matcher.group("purse").replace(",", "")));
                                    } catch (NumberFormatException e) {
                                        Util.notifyError("Failed to parse purse from scoreboard", e);
                                    }
                                }
                            }
                        }

                        return Optional.empty();
                    });

            if (purse.isEmpty()) return Optional.empty();

            Optional<PlayerInventory> playerInventory = Optional.ofNullable(MinecraftClient.getInstance())
                    .flatMap(client -> Optional.ofNullable(client.player))
                    .map(ClientPlayerEntity::getInventory);

            if (playerInventory.isEmpty()) return Optional.empty();

            return Optional.of(new TransactionState(purse.get(), productId.get(), productItem.get(), inputSign.get(), playerInventory.get(), container.get()));
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
            OptionalDouble price = BazaarDataUtil.findItemPriceOptional(state.productId(), getTransactionType());

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
                            .map(GenericContainerScreen::getScreenHandler)
                            .map(GenericContainerScreenHandler::getInventory)
                            .map(inventory -> SlotLookup.getInventoryItem(inventory, BazaarSlots.INSTANT_BUY.INPUT_FILLING_AMOUNT.slot))
                            .map(ItemInfo::itemStack)
                            .flatMap(BazaarScreens::findOptionAmount)
                            .map(value -> (int) Math.floor(value))
                            .orElse((int) state.playerInventory()
                                    .getMainStacks()
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
                        int amountCanAfford = (int) (state.purse() / OrderUtil.getPriceForPosition(state.productId(), PricingPosition.COMPETITIVE, getTransactionType()));

                        yield BazaarScreens.findBuyOrderAmountLimit(state.inputSign().itemStack())
                                .map(limit -> Math.min(amountCanAfford, limit))
                                .orElse(amountCanAfford);
                    }
                    yield state.playerInventory().getMainStacks().stream()
                            .filter(stack -> !stack.isEmpty())
                            .filter(stack -> Optional.ofNullable(stack.getCustomName())
                                    .map(Text::getString)
                                    .flatMap(BazaarDataUtil::findProductIdOptional)
                                    .map(productId -> productId.equals(state.productId()))
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
                String productId,

                @NotNull
                ItemInfo inputSign,

                @NotNull
                GenericContainerScreen containerScreen
        ) implements SignInputState {
        }

        /**
         * The prospect with which to resolve the output value.
         */
        protected abstract PricingPosition getPricingPosition();

        protected Optional<String> getItemProductId(ItemInfo inputSign) {
            return ScreenManager.getInstance()
                    .findBack(BazaarScreens.ITEM_PAGE)
                    .flatMap(BazaarScreenHandler::getDisplayProductId);
        }

        @Override
        protected Optional<TransactionState> makeState(ChestLoadedEvent event) {
            Optional<GenericContainerScreen> container = ScreenManager.getInstance()
                    .current()
                    .flatMap(context -> context.as(GenericContainerScreen.class));

            Optional<Inventory> inventory = container
                    .map(GenericContainerScreen::getScreenHandler)
                    .map(GenericContainerScreenHandler::getInventory);

            if (container.isEmpty() || inventory.isEmpty()) return Optional.empty();

            Optional<ItemInfo> inputSign = inventory.flatMap(this::getInputSign);

            if (inputSign.isEmpty()) return Optional.empty();

            Optional<String> productId = getItemProductId(inputSign.get());

            if (productId.isEmpty()) return Optional.empty();

            return Optional.of(new TransactionState(productId.get(), inputSign.get(), container.get()));
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
            OptionalDouble price = BazaarDataUtil.findItemPriceOptional(state.productId(), getTransactionType());

            if (price.isEmpty()) {
                Util.logMessage("Could not retrieve relevant item pricing for " + name + "'s resolved value.");

                return new ResolvedInput.Value(0);
            }

            return new ResolvedInput.Value(OrderUtil.getPriceForPosition(state.productId(), getPricingPosition(), getTransactionType()));
        }
    }

    public abstract static class TransactionFlip extends TransactionCost {
        public static final Pattern VOLUME_PATTERN = Pattern.compile("([\\d,]+)");
        public static final int INPUT_LORE_LINE_VOLUME = 1;

        public static final Pattern PRICE_PATTERN = Pattern.compile("([\\d,.]+) coins");
        public static final int INPUT_LORE_LINE_PRICE = 3;

        public TransactionFlip(@NotNull String name, @NotNull BazaarSlots.BazaarSlot inputSignRef) {
            super(name, inputSignRef);
        }

        @Override
        protected Optional<String> getItemProductId(ItemInfo inputSign) {
            List<Text> loreLines = LoreParser.lines(inputSign.itemStack());
            if (loreLines.isEmpty()) return Optional.empty();
            return matchToUserOrder(loreLines).map(Order::getProductID);
        }

        private Optional<Order> matchToUserOrder(List<Text> loreLines) {
            Optional<PriceInfo> priceInfo = getOrderPriceInfo(loreLines);
            Optional<Integer> volume = getVolumeUnclaimed(loreLines);

            if (priceInfo.isEmpty() || volume.isEmpty()) return Optional.empty();

            OrderInfo tempOrder = new OrderInfo(
                    null,
                    priceInfo.get().getTransactionType().getSide(),
                    null,
                    volume.get(),
                    priceInfo.get().getPricePerItem(),
                    null
            );

            return tempOrder.findOrderInList(UserOrdersStorage.INSTANCE.get());
        }

        private Optional<PriceInfo> getOrderPriceInfo(List<Text> loreLines) {
            if (loreLines.size() <= INPUT_LORE_LINE_PRICE) return Optional.empty();

            Matcher matcher = PRICE_PATTERN.matcher(loreLines.get(INPUT_LORE_LINE_PRICE).getString());
            if (matcher.find()) {
                try {
                    // Flip orders are always on the buy side; the sell price is computed after matching
                    return Optional.of(new PriceInfo(Double.parseDouble(matcher.group(1).replace(",", "")), TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER)));
                } catch (NumberFormatException e) {
                    Util.notifyError("Error parsing order price in TransactionFlip", e);
                }
            }

            return Optional.empty();
        }

        private Optional<Integer> getVolumeUnclaimed(List<Text> loreLines) {
            if (loreLines.size() <= INPUT_LORE_LINE_VOLUME) return Optional.empty();

            Matcher matcher = VOLUME_PATTERN.matcher(loreLines.get(INPUT_LORE_LINE_VOLUME).getString());
            if (matcher.find()) {
                try {
                    return Optional.of(Integer.parseInt(matcher.group(1).replace(",", "")));
                } catch (NumberFormatException e) {
                    Util.notifyError("Error parsing order volume in TransactionFlip", e);
                }
            }

            return Optional.empty();
        }
    }
}