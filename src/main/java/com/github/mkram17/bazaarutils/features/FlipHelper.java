package com.github.mkram17.bazaarutils.features;


import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.*;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.misc.CustomItemButton;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.VersionCompat;

import dev.isxander.yacl3.api.NameableEnum;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//TODO switch to finding market price without finding the OrderData first. Then, OrderUpdater should handle fixing it. Or just do it that way for redundancy.
public class FlipHelper extends CustomItemButton implements BUListener {

    private static final int FLIP_ORDER_SLOT = 15;
    private static final Pattern PRICE_PATTERN = Pattern.compile("([\\d,.]+) coins");
    private static final Pattern VOLUME_PATTERN = Pattern.compile("([\\d,]+)");
    private static final String FLIP_ORDER_IDENTIFIER = "Flip Order";
    private static final String CANNOT_CANCEL_IDENTIFIER = "can't be flipped";
    private static final int LORE_LINE_VOLUME = 1;
    private static final int LORE_LINE_PRICE = 3;

    public enum BiddingType implements NameableEnum {
      COMPETITIVE,
      MATCHED,
      OUTBIDDED;

      @Override
      public Component getDisplayName() {
        return Component.nullToEmpty(name());
      }
    }

    @Getter @Setter
    private boolean enabled;
    @Getter @Setter
    private BiddingType biddingType;

    @Getter
    private static final Item BUTTON_ITEM = Items.CHERRY_SIGN;
    private BazaarOrder order;

    public FlipHelper(boolean enabled, BiddingType biddingType, int slotNumber) {
        this.enabled = enabled;
        this.biddingType = biddingType;
        this.slotNumber = slotNumber;
    }

    public static OptionGroup.Builder createFlipsGroup() {
      return OptionGroup.createBuilder()
              .name(Component.literal("Flip Helper Options"))
              .description(OptionDescription.of(Component.literal("Manage buttons of flip helper action.")));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChestLoaded(ChestLoadedEvent e) {
        if (!enabled) {
            return;
        }
        if(!inCorrectScreen()){
            resetState();
            return;
        }

        try {
            ItemStack flipOrderSign = getFlipSign(e.getItemStacks()).orElse(new ItemStack(Items.BARRIER, 1));
            Optional<BazaarOrder> orderOptional = matchToUserOrder(flipOrderSign.getComponents().get(DataComponents.LORE));
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
        if (!enabled || event.slot.getContainerSlot() != slotNumber || !inCorrectScreen() || order == null) {
            return;
        }

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);
        GUIUtils.clickSlot(FLIP_ORDER_SLOT,0);
        GUIUtils.runOnNextSignOpen(signOpenEvent -> handleFlip());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void replaceItemEvent(ReplaceItemEvent event) {
        if(!enabled || !(event.getSlotId() == slotNumber) || !inCorrectScreen() || order == null)
            return;

        ItemStack itemStack = new ItemStack(BUTTON_ITEM, 1);
        itemStack.set(DataComponents.CUSTOM_NAME, getButtonText());
        itemStack.set(BazaarUtils.CUSTOM_SIZE_COMPONENT, getButtonStackSize());
        event.setReplacement(itemStack);
    }

    private Component getButtonText() {
        double flipPrice = computeFlipPrice(order);
        if (flipPrice == 0) {
            return Component.literal("There are no competing sell offers.").withStyle(ChatFormatting.DARK_PURPLE);
        } else if (order == null) {
            return Component.literal("Could not find order").withStyle(ChatFormatting.DARK_PURPLE);
        } else {
            return Component.literal("Flip order for " + Util.getPrettyString(flipPrice) + " coins").withStyle(ChatFormatting.DARK_PURPLE);
        }
    }

    private String getButtonStackSize() {
        double flipPrice = computeFlipPrice(order);
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
        double flipPrice = computeFlipPrice(order);
        ScreenInfo previousScreen = ScreenInfo.getCurrentScreenInfo().getPreviousScreenInfo();
        if(order != null && flipPrice != 0 && previousScreen.inMenu(ScreenInfo.BazaarMenuType.FLIP_GUI)) {
            GUIUtils.setSignText(Double.toString(Util.truncateNum(flipPrice)), true);
            order.flipItem(flipPrice);
        }
    }

    private double computeFlipPrice(BazaarOrder order) {
        PriceInfoContainer.PriceType currentType = order.getPriceType();
        double marketOppositePrice = order.getMarketPrice(currentType.getOpposite());

        if (marketOppositePrice <= 0) return 0;

        // Users with config from before this option was added will have null value for the biddingType variable. This ensures a default value is set.
        if(biddingType == null) {
            biddingType = BiddingType.COMPETITIVE;
        }

        return switch (biddingType) {
            case COMPETITIVE -> order.getFlipPrice();
            case MATCHED -> Util.truncateNum(marketOppositePrice);
            case OUTBIDDED -> order.getOutbiddedPrice();
        };
    }

    private Optional<ItemStack> getFlipSign(List<ItemStack> chestItemStacks) {
        for (ItemStack itemStack : chestItemStacks) {
            if (itemStack == null || itemStack.isEmpty()) {
                continue;
            }

            if (itemStack.getHoverName().getString().contains(FLIP_ORDER_IDENTIFIER)) {
                ItemLore lore = itemStack.getComponents().get(DataComponents.LORE);
                if (lore != null) {
                    return Optional.of(itemStack);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<PriceInfoContainer> getOrderPriceInfo(ItemLore lore) {
        if (lore.lines().size() <= LORE_LINE_PRICE) return Optional.empty();

        String priceLine = lore.lines().get(LORE_LINE_PRICE).getString();
        Matcher matcher = PRICE_PATTERN.matcher(priceLine);

        if (matcher.find()) {
            try {
                double orderPrice = Double.parseDouble(matcher.group(1).replace(",", ""));
                return Optional.of(new PriceInfoContainer(orderPrice, PriceInfoContainer.PriceType.INSTASELL));
            } catch (NumberFormatException e) {
                Util.notifyError("Error while trying to parse order price in Flip Helper", e);
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> getVolumeUnclaimed(ItemLore lore) {
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

    private Optional<BazaarOrder> matchToUserOrder(ItemLore lore) {
        Optional<PriceInfoContainer> priceInfoOpt = getOrderPriceInfo(lore);
        Optional<Integer> orderVolumeFilledOpt = getVolumeUnclaimed(lore);

        if (priceInfoOpt.isPresent() && orderVolumeFilledOpt.isPresent()) {
            PriceInfoContainer priceInfoContainer = priceInfoOpt.get();
            OrderInfoContainer tempOrder = new OrderInfoContainer(null, orderVolumeFilledOpt.get(), priceInfoContainer.getPricePerItem(), priceInfoContainer.getPriceType(), null);
            return tempOrder.findOrderInList(BUConfig.get().userOrders);
        }
        return Optional.empty();
    }

    private static boolean inCorrectScreen(){
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        return screenInfo.inMenu(ScreenInfo.BazaarMenuType.FLIP_GUI) && !inCancelOrderScreen();
    }

    private static boolean inCancelOrderScreen() {
        if (!(VersionCompat.getScreen(Minecraft.getInstance()) instanceof ContainerScreen inventory)) {
            return false;
        }

        try {
            return cantBeFlippedLineIsPresent(inventory, FLIP_ORDER_SLOT);
        } catch (Exception ex) {
            Util.notifyError("Error while checking if in cancel screen", ex);
            return false;
        }
    }

    private static boolean cantBeFlippedLineIsPresent(ContainerScreen inventory, int slot){
        ItemStack itemStack = inventory.getMenu().getContainer().getItem(slot);
        if (itemStack.isEmpty()) {
            return false;
        }

        Component customName = itemStack.get(DataComponents.CUSTOM_NAME);
        if (customName == null || !customName.getString().contains(FLIP_ORDER_IDENTIFIER)) {
            return false;
        }

        ItemLore lore = itemStack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return false;
        }
        return Util.findComponentWith(lore.lines(), CANNOT_CANCEL_IDENTIFIER) != null;
    }

    //an item to cancel the order being present means that the order has not been filled or is otherwise not ready to be flipped
//    private static boolean isCancelItem(GenericContainerScreen inventory, int slot) {
//        ItemStack itemStack = inventory.getScreenHandler().getInventory().getStack(slot);
//        if (itemStack.isEmpty()) {
//            return false;
//        }
//
//        Text customName = itemStack.get(DataComponentTypes.CUSTOM_NAME);
//        if (customName != null && customName.getString().contains(CANCEL_ORDER_IDENTIFIER)) {
//            return true;
//        }
//
//        LoreComponent lore = itemStack.get(DataComponentTypes.LORE);
//        if (lore != null) {
//            return lore.lines().stream()
//                    .noneMatch(line -> line.getString().contains(CANNOT_CANCEL_IDENTIFIER));
//        }
//
//        return false;
//    }

    public Option<Boolean> createOption() {
        return super.createBooleanOption("Flip Helper",
                "Button in flip order menu to undercut market prices for items.",
                this::isEnabled,
                this::setEnabled);
    }

    public Option<BiddingType> createFlippingTypeOption() {
        // Users with config from before this option was added will have null value for the biddingType variable. This ensures a default value is set.
        if(biddingType == null) {
          biddingType = BiddingType.COMPETITIVE;
        }

      return super.createEnumOption("Bidding type",
          "Select how the flip price should be chosen.",
          BiddingType.class,
          biddingType,
          this::getBiddingType,
          this::setBiddingType);
    }

    public static void buildOptions(OptionGroup.Builder builder) {
      FlipHelper flipHelper = BUConfig.get().flipHelper;

      builder.option(flipHelper.createFlippingTypeOption());  
    }

    @Override
    public void subscribe() {
        EVENT_BUS.subscribe(this);
    }
}

