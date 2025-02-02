package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import com.github.mkram17.bazaarutils.Utils.CustomItemButton;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Supplier;

public class CustomOrder extends CustomItemButton {
    private boolean buySignClicked = false;
    private final Supplier<Integer> orderAmount;
    public static ConfigCategory.Builder createOrdersCategory(){
        return ConfigCategory.createBuilder()
                .name(Text.literal("Buy Orders"));
    }
    public int getOrderAmount() {
        return orderAmount.get();
    }
    public CustomOrder(Supplier<Boolean> enabled, Supplier<Integer> orderAmount, Supplier<Integer> replaceSlotNumber, Item item) {
        super(enabled, replaceSlotNumber, item);
        this.orderAmount = orderAmount;
    }

    @Override
    @EventHandler
    public void onGUI(ReplaceItemEvent event) {
        if (!BazaarUtils.gui.inBuyOrderScreen() || !isEnabled())
            return;

        if (event.getSlotId() != getReplaceSlotNumber())
            return;

        ItemStack itemStack = new ItemStack(getButtonItem(), getOrderAmount());

// Set the display name
        itemStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Buy " + getOrderAmount()).formatted(Formatting.DARK_PURPLE));
        event.setReplacement(itemStack);
    }

    @Override
    @EventHandler
    public void onSlotClicked(SlotClickEvent event) {
        if (!BazaarUtils.gui.inBuyOrderScreen() || !isEnabled())
            return;

        if (event.slot.getIndex() != getReplaceSlotNumber())
            return;

        openSign();
    }

    @EventHandler
    private void onSignOpened(SignOpenEvent event) {
        if (!buySignClicked) return;

        GUIUtils.setSignText(Integer.toString(getOrderAmount()));
//        GUIUtils.closeGui();
        buySignClicked = false;
    }

    public void openSign() {
        int signSlotId = getReplaceSlotNumber() - 1;
        GUIUtils.clickSlot(signSlotId, 0);
        buySignClicked = true;
    }

    @Override
    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal(getOrderAmount() == 71680 ? "Buy Max Button" : "Enable " + getOrderAmount() + " Button"))
                .binding(isEnabled(),
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfig::createBooleanController)
                .build();
    }
}
