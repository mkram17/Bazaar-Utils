package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount;

import com.github.mkram17.bazaarutils.config.util.api.SlotProviders;
import com.github.mkram17.bazaarutils.config.util.api.annotations.ContainerSlot;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.TransactionPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import com.teamresourceful.resourcefulconfig.api.types.info.ListEntryInfoProvider;
import lombok.Getter;
import net.minecraft.network.chat.Component;

import java.util.stream.IntStream;

@Getter
@ConfigObject
public class BuyOrderAmountHelper extends SignInputHelper.TransactionAmount implements ListEntryInfoProvider {
    @ConfigEntry(
            id = "item_id",
            translation = "bazaarutils.config.buttons.button.container.item_id.label"
    )
    @Comment(
            value = "The item that will be placed as the button.",
            translation = "bazaarutils.config.buttons.button.container.item_id.hint"
    )
    @ConfigOption.Renderer("bazaarutils:item")
    public String itemId = "minecraft:green_stained_glass_pane";

    @ConfigEntry(
            id = "slot_index",
            translation = "bazaarutils.config.buttons.button.container.slot_index.label"
    )
    @Comment(
            value = "The container slot where the button will be placed.",
            translation = "bazaarutils.config.buttons.button.container.slot_index.hint"
    )
    @ContainerSlot(rows = 4, cols = 9, provider = "bazaar:buy_order_amount")
    @ConfigOption.Range(min = 0, max = 35)
    @ConfigOption.Renderer("bazaarutils:slot")
    public int slotIndex;

    @ConfigEntry(
            id = "amount_strategy",
            translation = "bazaarutils.config.buttons.button.container.amount_strategy.label"
    )
    @Comment(
            value = """
                    The strategy with which to calculate the amount value.
                    
                    MAX: Will order the maximum amount you may order considering the action (instant, order).
                    FIXED: Will order whatever amount you've configured below.
                    """,
            translation = "bazaarutils.config.buttons.button.container.amount_strategy.hint"
    )
    public AmountStrategy amountStrategy = AmountStrategy.MAX;

    @ConfigEntry(
            id = "fixed_amount",
            translation = "bazaarutils.config.buttons.button.container.fixed_amount.label"
    )
    @Comment(
            value = "Amount used for FIXED input strategy.",
            translation = "bazaarutils.config.buttons.button.container.fixed_amount.hint"
    )
    public int fixedAmount = 1;

    public TransactionType transactionType = TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER);

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(this::getItemId);
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.BUY_ORDER_AMOUNT);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    public BuyOrderAmountHelper(int slotIndex) {
        super("Buy Order Amount Helper", BazaarSlots.BUY_ORDER.INPUT_CUSTOM_AMOUNT.slot);
        this.slotIndex = slotIndex;
    }

    public BuyOrderAmountHelper() {
        this(getNextSlotIndex());
    }

    @Override
    protected int computeFixedValue(TransactionState state) {
        return getFixedAmount();
    }

    @Override
    protected int computeMaxValue(TransactionAmount.TransactionState state) {
        int amountCanAfford = (int) (state.purse() / OrderUtil.getPriceForPosition(state.productId(), PricingPosition.COMPETITIVE, getTransactionType()));

        return TransactionPageLayout.findBuyOrderAmountLimit(state.inputSign().itemStack())
                            .map(limit -> Math.min(amountCanAfford, limit))
                            .orElse(amountCanAfford);
    }

    @Override
    protected Component getButtonItemText(TransactionState state) {
        return Component.nullToEmpty("Order " + getButtonItemStackSize(state) + " items.");
    }

    @Override
    public Component getTitle(int index) {
        return Component.literal(switch (amountStrategy) {
            case AmountStrategy.MAX -> "Orders MAX possible items";
            case AmountStrategy.FIXED -> "Orders " + fixedAmount + " items";
        });
    }

    @Override
    public Component getDescription(int index) {
        var stack = resolveStack();

        return Component.literal("Slot " + slotIndex + " · " + stack.getItem().getName(stack).getString());
    }

    private static int getNextSlotIndex() {
        return IntStream.rangeClosed(0, 35)
                .filter(i -> !SlotProviders.get("bazaar:buy_order_amount").getStack(i)
                        .getOrDefault(CustomDataComponents.SLOT_SELECTOR_LOCKED, false))
                .findFirst()
                .orElse(35);
    }
}