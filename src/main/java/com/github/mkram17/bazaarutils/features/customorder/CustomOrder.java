package com.github.mkram17.bazaarutils.features.customorder;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import com.github.mkram17.bazaarutils.Utils.CustomItemButton;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

public class CustomOrder extends CustomItemButton {
    private boolean buySignClicked = false;
    @Getter
    private CustomOrderSettings settings;

    public static final Map<Integer, Item> COLORMAP = new HashMap<>(Map.of(0, Items.PURPLE_STAINED_GLASS_PANE, 1, Items.BLUE_STAINED_GLASS_PANE, 2, Items.BLACK_STAINED_GLASS_PANE, 3, Items.GREEN_STAINED_GLASS_PANE));

    public static ConfigCategory.Builder createOrdersCategory(){
        return ConfigCategory.createBuilder()
                .name(Text.literal("Buy Orders"));
    }

    public CustomOrder(CustomOrderSettings settings){
        super(settings.isEnabled(), settings.getSlotNumber(), settings.getItem());
        this.settings = settings;
    }

    @Override
    @EventHandler
    public void onGUI(ReplaceItemEvent event) {
        if (!BazaarUtils.gui.inBuyOrderScreen() || !isEnabled())
            return;

        if (event.getSlotId() != getReplaceSlotNumber())
            return;

        ItemStack itemStack = new ItemStack(getItem(), getOrderAmount());

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
    private int getOrderAmount(){
        return settings.getOrderAmount();
    }
}
