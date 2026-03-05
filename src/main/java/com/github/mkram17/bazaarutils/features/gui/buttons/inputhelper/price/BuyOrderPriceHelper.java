package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price;

import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
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
public class BuyOrderPriceHelper extends SignInputHelper.TransactionCost {
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
            value = "The container slot where the button will be registered at",
            translation = "bazaarutils.config.buttons.button.container.slot_index.hint"
    )
    @ConfigOption.Range(min = 0, max = 35)
    public int slotIndex;

    @ConfigEntry(
            id = "pricing_position",
            translation = "bazaarutils.config.buttons.button.container.pricing_position.label"
    )
    @Comment(
            value = """
                    The strategy with which to calculate the price to bid per item
                    
                    COMPETITIVE: The bid will be +0.1 the current best offer on the market
                    MATCHED: The bid will be equal to that of the current best offer on the market
                    OUTBID: The bid will be -0.1 the current best offer on the market
                    """,
            translation = "bazaarutils.config.buttons.button.container.pricing_position.hint"
    )
    public PricingPosition pricingPosition;

    public TransactionType transactionType = TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER);

    @Override
    public Item getButtonItem() {
        return switch (getPricingPosition()) {
            case COMPETITIVE -> Items.GREEN_STAINED_GLASS_PANE;
            case MATCHED -> Items.YELLOW_STAINED_GLASS_PANE;
            case OUTBID -> Items.ORANGE_STAINED_GLASS_PANE;
        };
    }
    }

    @Override
    protected boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.BUY_ORDER_PRICE);
    }

    public BuyOrderPriceHelper(boolean enabled, int slotIndex, PricingPosition pricingPosition) {
        super("Buy Order Price Helper", BazaarSlots.BUY_ORDER.INPUT_CUSTOM_PRICE.slot);
        this.enabled = enabled;
        this.slotIndex = slotIndex;
        this.pricingPosition = pricingPosition;
    }

    @Override
    protected Text getButtonItemText(TransactionState state) {
        return Text.of("Bid " + getButtonItemStackSize(state) + " per item.");
    }
}
