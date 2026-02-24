package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper;


import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.*;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.util.ConfigurableFeature;
import com.github.mkram17.bazaarutils.ui.CustomItemButton;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;

import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODO switch to finding market price without finding the OrderData first. Then, OrderUpdater should handle fixing it. Or just do it that way for redundancy.
@ConfigObject
public class FlipHelper extends BUListener implements ConfigurableFeature, CustomItemButton {
    private static final int FLIP_ORDER_SLOT = 15;
    private static final Pattern PRICE_PATTERN = Pattern.compile("([\\d,.]+) coins");
    private static final Pattern VOLUME_PATTERN = Pattern.compile("([\\d,]+)");
    private static final String FLIP_ORDER_IDENTIFIER = "Flip Order";
    private static final int LORE_LINE_VOLUME = 1;
    private static final int LORE_LINE_PRICE = 3;

    @Getter @Setter @ConfigEntry(id = "enabled")
    private boolean enabled;
    @Getter @Setter @ConfigEntry(id = "pricingPosition")
    private PricingPosition pricingPosition;

    @Getter
    @ConfigEntry(
            id = "slot",
            translation = "bazaarutils.config.buttons.button.slot.value"
    )
    @Comment(
            value = "The container slot where the button will be registered at",
            translation = "bazaarutils.config.buttons.button.slot.description"
    )
    @ConfigOption.Range(min = 0, max = 35)
    public int slotNumber;

    @Getter
    private transient ItemStack replacementItem;

    @Getter
    private static final Item BUTTON_ITEM = Items.CHERRY_SIGN;
    private Order order;

    public FlipHelper(boolean enabled, PricingPosition pricingPosition, int slotNumber) {
        this.enabled = enabled;
        this.pricingPosition = pricingPosition;
        this.slotNumber = slotNumber;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChestLoaded(ChestLoadedEvent e) {
        if (!enabled) {
            return;
        }

        if (!inCorrectScreen()) {
            resetState();

            return;
        }

        try {
            ItemStack flipOrderSign = getFlipSign(e.getItemStacks()).orElse(new ItemStack(Items.BARRIER, 1));

            Optional<Order> orderOptional = matchToUserOrder(flipOrderSign.getComponents().get(DataComponentTypes.LORE));

            if (orderOptional.isEmpty()) {
                return;

            }
            order = orderOptional.get();
        } catch (Exception ex) {
            Util.notifyError("Error while trying to find flip item in Flip Helper", ex);
        }
    }

    @EventHandler
    public void onSlotClicked(SlotClickEvent event) {
        if (!enabled || event.slot.getIndex() != slotNumber || !inCorrectScreen() || order == null) {
            return;
        }

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        GUIUtils.clickSlot(FLIP_ORDER_SLOT,0);

        GUIUtils.runOnNextSignOpen(signOpenEvent -> handleFlip());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void replaceItemEvent(ReplaceItemEvent event) {
        if (!enabled || !(event.getSlotId() == slotNumber) || !inCorrectScreen() || order == null) {
            return;
        }

        ItemStack itemStack = new ItemStack(BUTTON_ITEM, 1);

        itemStack.set(DataComponentTypes.CUSTOM_NAME, getButtonText());
        itemStack.set(BazaarUtils.CUSTOM_SIZE_COMPONENT, getButtonStackSize());

        event.setReplacement(itemStack);
    }

    private Text getButtonText() {
        double flipPrice = computeFlipPrice();

        if (flipPrice == 0) {
            return Text.literal("There are no competing sell offers.").formatted(Formatting.DARK_PURPLE);
        } else if (order == null) {
            return Text.literal("Could not find order").formatted(Formatting.DARK_PURPLE);
        } else {
            return Text.literal("Flip order for " + Util.getPrettyString(flipPrice) + " coins").formatted(Formatting.DARK_PURPLE);
        }
    }

    private String getButtonStackSize() {
        double flipPrice = computeFlipPrice();

        if (flipPrice == 0) {
            return "ANY";
        } else if (order == null) {
            return "???";
        } else {
            return String.valueOf(Util.truncateNum(flipPrice));
        }
    }

    private void resetState() {
        this.order = null;
    }

    private void handleFlip() {
        double flipPrice = computeFlipPrice();

        ScreenInfo previousScreen = ScreenInfo.getCurrentScreenInfo().getPreviousScreenInfo();

        if (order != null && flipPrice != 0 && previousScreen.inMenu(ScreenInfo.BazaarMenuType.FLIP_GUI)) {
            GUIUtils.setSignText(Double.toString(Util.truncateNum(flipPrice)), true);

            order.flipItem(flipPrice);
        }
    }

    private double computeFlipPrice() {
        return switch (pricingPosition) {
            case COMPETITIVE -> order.getUndercutPrice(OrderType.SELL);
            case MATCHED -> order.getMarketPrice(OrderType.SELL);
            case OUTBID -> order.getOutbidPrice(OrderType.SELL);
        };
    }

    private Optional<ItemStack> getFlipSign(List<ItemStack> chestItemStacks) {
        for (ItemStack itemStack : chestItemStacks) {
            if (itemStack == null || itemStack.isEmpty()) {
                continue;
            }

            if (itemStack.getName().getString().contains(FLIP_ORDER_IDENTIFIER)) {
                LoreComponent lore = itemStack.getComponents().get(DataComponentTypes.LORE);

                if (lore != null) {
                    return Optional.of(itemStack);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<PriceInfo> getOrderPriceInfo(LoreComponent lore) {
        if (lore.lines().size() <= LORE_LINE_PRICE) {
            return Optional.empty();
        }

        String priceLine = lore.lines().get(LORE_LINE_PRICE).getString();

        Matcher matcher = PRICE_PATTERN.matcher(priceLine);

        if (matcher.find()) {
            try {
                double orderPrice = Double.parseDouble(matcher.group(1).replace(",", ""));

                return Optional.of(new PriceInfo(orderPrice, OrderType.BUY));
            } catch (NumberFormatException e) {
                Util.notifyError("Error while trying to parse order price in Flip Helper", e);
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> getVolumeUnclaimed(LoreComponent lore) {
        if (lore.lines().size() <= LORE_LINE_VOLUME) return Optional.empty();

        String volumeLine = lore.lines().get(LORE_LINE_VOLUME).getString();
        Matcher matcher = VOLUME_PATTERN.matcher(volumeLine);

        if (matcher.find()) {
            try {
                return Optional.of(Integer.parseInt(matcher.group(1).replace(",", "")));
            } catch (NumberFormatException e) {
                Util.notifyError("Error while trying to parse order volume in Flip Helper", e);
            }
        }
        return Optional.empty();
    }

    private Optional<Order> matchToUserOrder(LoreComponent lore) {
        Optional<PriceInfo> priceInfoOpt = getOrderPriceInfo(lore);
        Optional<Integer> orderVolumeFilledOpt = getVolumeUnclaimed(lore);

        if (priceInfoOpt.isPresent() && orderVolumeFilledOpt.isPresent()) {
            PriceInfo priceInfo = priceInfoOpt.get();
            OrderInfo tempOrder = new OrderInfo(null, priceInfo.getOrderType(), null, orderVolumeFilledOpt.get(), priceInfo.getPricePerItem(), null);

            return tempOrder.findOrderInList(UserOrdersStorage.INSTANCE.get());
        }
        return Optional.empty();
    }

    private static boolean inCorrectScreen() {
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();

        return screenInfo.inMenu(ScreenInfo.BazaarMenuType.FLIP_GUI) && !screenInfo.inMenu(ScreenInfo.BazaarMenuType.CANCEL_ORDER);
    }
}
