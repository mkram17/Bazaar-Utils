package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount;

import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import lombok.Getter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

@Getter
@ConfigObject
public class SellOfferAmountHelper extends SignInputHelper.TransactionAmount {

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
            id = "slot_index",
            translation = "bazaarutils.config.buttons.button.container.slot_index.label"
    )
    @Comment(
            value = "The container slot where the button will be registered at",
            translation = "bazaarutils.config.buttons.button.container.slot_index.hint"
    )
    @ConfigOption.Range(min = 0, max = 35)
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

    public TransactionType transactionType = TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER);

    @Override
    public Item getButtonItem() {
        return Items.GREEN_STAINED_GLASS_PANE;
    }

    }

    @Override
    protected boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.SELL_ORDER_AMOUNT);
    }

    public SellOfferAmountHelper(boolean enabled, int slotIndex) {
        super("Sell Offer Amount Helper", BazaarSlots.SELL_OFFER.INPUT_CUSTOM_AMOUNT.slot);
        this.enabled = enabled;
        this.slotIndex = slotIndex;
    }

    @Override
    protected int computeFixedValue(TransactionState state) {
        return getFixedAmount();
    }

    @Override
    protected Text getButtonItemText(TransactionState state) {
        return Text.of("Offer " + getButtonItemStackSize(state) + " items.");
    }
}