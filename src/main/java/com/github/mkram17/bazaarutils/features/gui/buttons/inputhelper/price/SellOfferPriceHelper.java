package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price;

import com.github.mkram17.bazaarutils.config.util.api.SlotProviders;
import com.github.mkram17.bazaarutils.config.util.api.annotations.ContainerSlot;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
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

import java.util.stream.IntStream;

@Getter
@ConfigObject
public class SellOfferPriceHelper extends SignInputHelper.TransactionCost implements ListEntryInfoProvider {
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
    @ContainerSlot(rows = 4, cols = 9, provider = "bazaar:sell_offer_price")
    @ConfigOption.Range(min = 0, max = 35)
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

    public TransactionType transactionType = TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER);

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(this::getItemId);
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.SELL_OFFER_PRICE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public SellOfferPriceHelper(int slotIndex, PricingPosition pricingPosition) {
        super("Sell Offer Price Helper", BazaarSlots.SELL_OFFER.INPUT_CUSTOM_PRICE.slot);
        this.slotIndex = slotIndex;
        this.pricingPosition = pricingPosition;
    }

    public SellOfferPriceHelper() {
        this(getNextSlotIndex(), PricingPosition.COMPETITIVE);
    }

    @Override
    protected Component getButtonItemText(TransactionState state) {
        return Component.nullToEmpty("Ask " + getButtonItemStackSize(state) + " per item.");
    }

    @Override
    public Component getTitle(int index) {
        return Component.literal(switch (pricingPosition) {
            case COMPETITIVE -> "Asks +0.1 above best bid";
            case MATCHED -> "Asks equal to best bid";
            case OUTBID -> "Asks -0.1 below best bid";
        });
    }

    @Override
    public Component getDescription(int index) {
        return Component.literal("Slot " + slotIndex + " · " + resolveStack().getItem().getName().getString());
    }

    private static int getNextSlotIndex() {
        return IntStream.rangeClosed(0, 35)
                .filter(i -> !SlotProviders.get("bazaar:sell_offer_price").getStack(i)
                        .getOrDefault(CustomDataComponents.SLOT_SELECTOR_LOCKED, false))
                .findFirst()
                .orElse(35);
    }
}
