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
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomOrder extends CustomItemButton {
    public static final Map<Integer, Item> COLORMAP = new HashMap<>(Map.of(0, Items.PURPLE_STAINED_GLASS_PANE, 1, Items.BLUE_STAINED_GLASS_PANE, 2, Items.ORANGE_STAINED_GLASS_PANE, 3, Items.GREEN_STAINED_GLASS_PANE));
    private boolean buySignClicked = false;
    @Getter @Setter
    private CustomOrderSettings settings;

    public static ConfigCategory.Builder createOrdersCategory(){
        return ConfigCategory.createBuilder()
                .name(Text.literal("Buy Amount Options"));
    }

    public CustomOrder(CustomOrderSettings settings){
        this.settings = settings;
        BazaarUtils.eventBus.subscribe(this);
    }

    @Override
    @EventHandler
    public void onGUI(ReplaceItemEvent event) {
//        if(getOrderAmount() != 71680 && event.getSlotId() == 8)
//            Util.notifyAll(isEnabled());
        if (!(BazaarUtils.gui.inBuyOrderScreen() || BazaarUtils.gui.inInstaBuy())|| !settings.isEnabled())
            return;

        if (event.getSlotId() != settings.getSlotNumber())
            return;

        ItemStack itemStack = new ItemStack(settings.getItem(), settings.getOrderAmount());

// Set the display name
        itemStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Buy " + settings.getOrderAmount()).formatted(Formatting.DARK_PURPLE));
        event.setReplacement(itemStack);
    }

    @Override
    @EventHandler
    public void onSlotClicked(SlotClickEvent event) {
        if (!(BazaarUtils.gui.inBuyOrderScreen() || BazaarUtils.gui.inInstaBuy()) || !settings.isEnabled())
            return;

        if (event.slot.getIndex() != settings.getSlotNumber())
            return;

        openSign();
    }

    @EventHandler
    private void onSignOpened(SignOpenEvent event) {
        if (!buySignClicked) return;

        GUIUtils.setSignText(Integer.toString(settings.getOrderAmount()));
        GUIUtils.closeGui();
        buySignClicked = false;
    }

    public void openSign() {
        int signSlotId = 16;
        GUIUtils.clickSlot(signSlotId, 0);
        buySignClicked = true;
    }

    @Override
    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal(settings.getOrderAmount() == 71680 ? "Buy Max Button" : "Buy " + settings.getOrderAmount() + " Button"))
                .binding(true,
                        () -> settings.isEnabled(),
                        (newVal) -> settings.setEnabled(newVal))
                .description(OptionDescription.of(Text.literal("Buy order button for " + settings.getOrderAmount() + " of an item.")))
                .controller(BUConfig::createBooleanController)
                .build();
    }
    public static void buildOptions(OptionGroup.Builder builder){
        List<CustomOrder> customOrders = BUConfig.get().customOrders;
        if(customOrders.isEmpty())
            customOrders.add(new CustomOrder(new CustomOrderSettings(true, 71680, 17, CustomOrder.COLORMAP.get(0))));

        for (CustomOrder order : customOrders) {
            builder.option(order.createOption());
        }
    }
}
