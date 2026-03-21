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
public class InstantBuyAmountHelper extends SignInputHelper.TransactionAmount {

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
            id = "slot_number",
            translation = "bazaarutils.config.buttons.button.container.slot_number.label"
    )
    @Comment(
            value = "The container slot where the button will be registered at",
            translation = "bazaarutils.config.buttons.button.container.slot_number.hint"
    )
    @ConfigOption.Range(min = 0, max = 35)
    public int slotNumber;

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

    public TransactionType transactionType = TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.INSTANT);

    @Override
    public Item getButtonItem() {
        return Items.GREEN_STAINED_GLASS_PANE;
    }

    @Override
    public ItemStack getReplacementItem() {
        return new ItemStack(this::getButtonItem, 1);
    }

    @Override
    protected boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.INSTANT_BUY);
    }

    public InstantBuyAmountHelper(boolean enabled, int slotNumber) {
        super("Instant Buy Amount Helper", BazaarSlots.INSTANT_BUY.INPUT_CUSTOM_AMOUNT.slot);
        this.enabled = enabled;
        this.slotNumber = slotNumber;
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