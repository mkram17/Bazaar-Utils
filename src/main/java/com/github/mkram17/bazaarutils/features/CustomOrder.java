package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Supplier;

public class CustomOrder {
    private final Supplier<Boolean> enabled;
    private final Supplier<Integer> orderAmount;
    private final Supplier<Integer> replaceSlotNumber;
    private final Item item;
    private boolean signClicked = false;

    public boolean isEnabled() {
        return enabled.get();
    }
    public int getOrderAmount() {
        return orderAmount.get();
    }
    public int getReplaceSlotNumber() {
        return replaceSlotNumber.get();
    }


    public CustomOrder(Supplier<Boolean> enabled, Supplier<Integer> orderAmount, Supplier<Integer> replaceSlotNumber, Item item) {
        this.enabled = enabled;
        this.orderAmount = orderAmount;
        this.replaceSlotNumber = replaceSlotNumber;
        this.item = item;
    }


    @EventHandler
    private void onGUI(ReplaceItemEvent event) {
        if (!BazaarUtils.gui.inBuyOrderScreen() || !isEnabled())
            return;

        if (event.getSlotId() != getReplaceSlotNumber())
            return;

        ItemStack purpleGlassPane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE, getOrderAmount());

// Set the display name
        purpleGlassPane.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Buy " + getOrderAmount()).formatted(Formatting.DARK_PURPLE));
        event.setReplacement(purpleGlassPane);
    }

    @EventHandler
    private void onSlotClicked(SlotClickEvent event) {
        if (!BazaarUtils.gui.inBuyOrderScreen() || !isEnabled())
            return;

        if (event.slot.getIndex() != getReplaceSlotNumber())
            return;

        openSign();
    }

    @EventHandler
    private void onSignOpened(SignOpenEvent event) {
        if (!signClicked) return;

        GUIUtils.setSignText(Integer.toString(getOrderAmount()), true);
//        GUIUtils.closeGui();
        signClicked = false;
    }

    public void openSign() {
        int signSlotId = getReplaceSlotNumber() - 1;
        GUIUtils.clickSlot(signSlotId, 0);
        signClicked = true;
    }
}
