package com.github.mkram17.bazaarutils.features.customorder;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import com.github.mkram17.bazaarutils.Utils.CustomItemButton;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Binding;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.StateManager;
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
import java.util.Map;

public class CustomOrder extends CustomItemButton {
    public static final Map<Integer, Item> COLORMAP = new HashMap<>(Map.of(0, Items.PURPLE_STAINED_GLASS_PANE, 1, Items.BLUE_STAINED_GLASS_PANE, 2, Items.ORANGE_STAINED_GLASS, 3, Items.GREEN_STAINED_GLASS_PANE));
    private boolean buySignClicked = false;
    @Getter @Setter
    private CustomOrderSettings settings;
    private transient StateManager<CustomOrderSettings> settingsStateManager;

    public boolean isEnabled(){
        return this.settingsStateManager.get().isEnabled();
    }
    public void setEnabled(boolean newEnabled){
        this.settingsStateManager.set(new CustomOrderSettings(newEnabled, getOrderAmount(), getReplaceSlotNumber(), getItem()));
        this.settings.setEnabled(newEnabled);
        this.settingsStateManager.sync();
    }
    public Item getItem(){
        return settingsStateManager.get().getItem();
    }
    public int getReplaceSlotNumber(){
        return settingsStateManager.get().getSlotNumber();
    }

    public static ConfigCategory.Builder createOrdersCategory(){
        return ConfigCategory.createBuilder()
                .name(Text.literal("Buy Orders"));
    }

    public CustomOrder(CustomOrderSettings settings){
        this.settings = settings;
        initializeStateManager();
        BazaarUtils.eventBus.subscribe(this);
    }

    @Override
    @EventHandler
    public void onGUI(ReplaceItemEvent event) {
//        if(getOrderAmount() != 71680 && event.getSlotId() == 8)
//            Util.notifyAll(isEnabled());
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
                .name(Text.literal(getOrderAmount() == 71680 ? "Buy Max Button" : "Enable " + getOrderAmount() + " Button"))
                .binding(true,
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfig::createBooleanController)
                .build();
    }
    private int getOrderAmount(){
        return settings.getOrderAmount();
    }

    public void initializeStateManager(){
        // Default value
        // Getter
        // Setter
        Binding<CustomOrderSettings> settingsBinding = Binding.generic(
                getSettings(), // Default value
                this::getSettings, // Getter
                this::setSettings // Setter
        );
        settingsStateManager = StateManager.createSimple(settingsBinding);
        settingsStateManager.sync();
    }
}
