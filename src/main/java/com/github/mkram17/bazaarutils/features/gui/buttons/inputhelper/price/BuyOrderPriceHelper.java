package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price;

import com.github.mkram17.bazaarutils.config.util.api.SlotProviders;
import com.github.mkram17.bazaarutils.config.util.api.annotations.ContainerSlot;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import lombok.Getter;
import net.minecraft.network.chat.Component;

@Getter
@ConfigObject
public class BuyOrderPriceHelper extends SignInputHelper.TransactionCost {
    /** Key of the slot layout this button is placed on; see {@code SlotRendererProvider}. */
    private static final String SLOT_PROVIDER = "bazaar:buy_order_price";

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
    @ContainerSlot(rows = 4, cols = 9, provider = SLOT_PROVIDER)
    @ConfigOption.Range(min = 0, max = SlotProviders.MAX_SLOT_INDEX)
    @ConfigOption.Renderer("bazaarutils:slot")
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

    public TransactionType transactionType = TransactionType.BUY_ORDER;

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.BUY_ORDER_PRICE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    public BuyOrderPriceHelper(int slotIndex, PricingPosition pricingPosition) {
        super("Buy Order Price Helper", BazaarSlots.BUY_ORDER.INPUT_CUSTOM_PRICE.slot);
        this.slotIndex = slotIndex;
        this.pricingPosition = pricingPosition;
    }

    public BuyOrderPriceHelper() {
        this(SlotProviders.firstUnlockedSlot(SLOT_PROVIDER), PricingPosition.COMPETITIVE);
    }

    @Override
    protected Component getButtonItemText(TransactionState state) {
        return Component.nullToEmpty("Bid " + getButtonItemStackSize(state) + " per item.");
    }

    @Override
    public Component getTitle(int index) {
        return Component.literal(switch (pricingPosition) {
            case COMPETITIVE -> "Bids +0.1 above best offer";
            case MATCHED -> "Bids equal to best offer";
            case OUTBID -> "Bids -0.1 below best offer";
        });
    }
}
