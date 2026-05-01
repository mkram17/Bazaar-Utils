package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount;

import com.github.mkram17.bazaarutils.config.util.api.SlotProviders;
import com.github.mkram17.bazaarutils.config.util.api.annotations.ContainerSlot;
import com.github.mkram17.bazaarutils.config.util.api.annotations.ShowIf;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.TransactionPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import com.teamresourceful.resourcefulconfig.api.types.info.ListEntryInfoProvider;
import lombok.Getter;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.stream.IntStream;

@Getter
@ConfigObject
public class InstantBuyAmountHelper extends SignInputHelper.TransactionAmount implements ListEntryInfoProvider {
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
    @ContainerSlot(rows = 4, cols = 9, provider = "bazaar:instant_buy_amount")
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
    @ShowIf(SignInputHelper.TransactionAmount.WhenFixedStrategy.class)
    public int fixedAmount = 1;

    public TransactionType transactionType = TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.INSTANT);

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(this::getItemId);
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.INSTANT_BUY);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public InstantBuyAmountHelper(int slotIndex) {
        super("Instant Buy Amount Helper", BazaarSlots.INSTANT_BUY.INPUT_CUSTOM_AMOUNT.slot);
        this.slotIndex = slotIndex;
    }

    public InstantBuyAmountHelper() {
        this(getNextSlotIndex());
    }

    @Override
    protected int computeFixedValue(TransactionState state) {
        return getFixedAmount();
    }

    @Override
    protected int computeMaxValue(TransactionAmount.TransactionState state) {
        return SlotLookup.getInventoryItem(state.container(), BazaarSlots.INSTANT_BUY.INPUT_FILLING_AMOUNT.slot)
                .map(ItemInfo::itemStack)
                .flatMap(TransactionPageLayout::findOptionAmount)
                .map(value -> (int) Math.floor(value))
                .orElse(state.playerInventory()
                        .getNonEquipmentItems()
                        .stream()
                        .mapToInt(stack -> {
                            int maxStackSize = state.productItem().itemStack().getMaxStackSize();
                            if (stack.isEmpty()) return maxStackSize;

                            boolean isSameItem = Optional.ofNullable(stack.getCustomName())
                                    .map(Component::getString)
                                    .flatMap(ProductInfo::fromDisplayName)
                                    .map(info -> info.getProductId().equals(state.productInfo().getProductId()))
                                    .orElse(false);

                            return isSameItem ? maxStackSize - stack.getCount() : 0;
                        })
                        .sum()
                );
    }

    @Override
    protected Component getButtonItemText(TransactionState state) {
        return Component.nullToEmpty("Purchase " + getButtonItemStackSize(state) + " items.");
    }

    @Override
    public Component getTitle(int index) {
        return Component.literal(switch (amountStrategy) {
            case MAX -> "Buys MAX possible items";
            case FIXED -> "Buys " + fixedAmount + " items";
        });
    }

    @Override
    public Component getDescription(int index) {
        return Component.literal("Slot " + slotIndex + " · " + resolveStack().getItem().getName().getString());
    }

    private static int getNextSlotIndex() {
        return IntStream.rangeClosed(0, 35)
                .filter(i -> !SlotProviders.get("bazaar:instant_buy_amount").getStack(i)
                        .getOrDefault(CustomDataComponents.SLOT_SELECTOR_LOCKED, false))
                .findFirst()
                .orElse(35);
    }
}