package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price;

import com.github.mkram17.bazaarutils.config.util.api.SlotProviders;
import com.github.mkram17.bazaarutils.config.util.api.annotations.ContainerSlot;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import com.teamresourceful.resourcefulconfig.api.types.info.ListEntryInfoProvider;
import lombok.Getter;
import net.minecraft.text.Text;

import java.util.stream.IntStream;

@Getter
@ConfigObject
public class BuyOrderPriceHelper extends SignInputHelper.TransactionCost implements ListEntryInfoProvider {
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
    @ContainerSlot(rows = 4, cols = 9, provider = "bazaar:buy_order_price")
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

    public TransactionType transactionType = TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER);

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(this::getItemId);
    }

    @Override
    protected boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.BUY_ORDER_PRICE);
    }

    public BuyOrderPriceHelper(int slotIndex, PricingPosition pricingPosition) {
        super("Buy Order Price Helper", BazaarSlots.BUY_ORDER.INPUT_CUSTOM_PRICE.slot);
        this.slotIndex = slotIndex;
        this.pricingPosition = pricingPosition;
    }

    public BuyOrderPriceHelper() {
        this(getNextSlotIndex(), PricingPosition.COMPETITIVE);
    }

    @Override
    protected Text getButtonItemText(TransactionState state) {
        return Text.of("Bid " + getButtonItemStackSize(state) + " per item.");
    }

    @Override
    public Text getTitle(int index) {
        return Text.literal(switch (pricingPosition) {
            case COMPETITIVE -> "Bids +0.1 above best offer";
            case MATCHED -> "Bids equal to best offer";
            case OUTBID -> "Bids -0.1 below best offer";
        });
    }

    @Override
    public Text getDescription(int index) {
        return Text.literal("Slot " + slotIndex + " · " + resolveItem().getName().getString());
    }

    private static int getNextSlotIndex() {
        return IntStream.rangeClosed(0, 35)
                .filter(i -> !SlotProviders.get("bazaar:buy_order_price").getStack(i)
                        .getOrDefault(CustomDataComponents.SLOT_SELECTOR_LOCKED, false))
                .findFirst()
                .orElse(35);
    }
}
