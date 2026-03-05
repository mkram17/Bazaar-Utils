package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount;

import com.github.mkram17.bazaarutils.config.util.api.annotations.ContainerSlot;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import lombok.Getter;
import net.minecraft.text.Text;

@Getter
@ConfigObject
public class BuyOrderAmountHelper extends SignInputHelper.TransactionAmount {
    @ConfigEntry(
            id = "enabled",
            translation = "bazaarutils.config.buttons.button.container.enabled.label"
    )
    @Comment(
            value = "Whether the button will be registered or not",
            translation = "bazaarutils.config.buttons.button.container.enabled.hint"
    )
    public boolean enabled;

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

    @Override
    protected boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.BUY_ORDER_AMOUNT);
    }

    public BuyOrderAmountHelper(boolean enabled, int slotIndex) {
        super("Buy Order Amount Helper", BazaarSlots.BUY_ORDER.INPUT_CUSTOM_AMOUNT.slot);
        this.enabled = enabled;
        this.slotIndex = slotIndex;
    }

    @Override
    protected int computeFixedValue(TransactionState state) {
        return getFixedAmount();
    }

    @Override
    protected Text getButtonItemText(TransactionState state) {
        return Text.of("Order " + getButtonItemStackSize(state) + " items.");
    }
}