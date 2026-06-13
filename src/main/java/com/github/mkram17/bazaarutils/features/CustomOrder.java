package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.misc.CustomItemButton;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//TODO low priority -- add number formating with commas (NumberFormat class?) for the tooltips to make large numbers easier to read
//TODO find new name for this
@NoArgsConstructor
public class CustomOrder extends CustomItemButton implements BUListener {
    public static final Map<Integer, Item> COLORMAP = new HashMap<>(Map.of(0, Items.PURPLE_STAINED_GLASS_PANE, 1, Items.BLUE_STAINED_GLASS_PANE, 2, Items.ORANGE_STAINED_GLASS_PANE, 3, Items.BLACK_STAINED_GLASS_PANE, 4, Items.BLACK_STAINED_GLASS_PANE));
    private boolean buySignClicked = false;

    @Getter @Setter
    private boolean enabled;
    @Getter @Setter
    private int orderAmount;
    @Getter
    private Item item;

    public CustomOrder(boolean enabled, int orderAmount, int slotNumber, Item item) {
        this.enabled = enabled;
        this.orderAmount = orderAmount;
        this.slotNumber = slotNumber;
        this.item = item;
        EVENT_BUS.subscribe(this);
    }
    protected CustomOrder(boolean enabled) {
        this.enabled = enabled;
        this.orderAmount = 71680;
        this.slotNumber = 17;
        this.item = Items.PURPLE_STAINED_GLASS_PANE;
    }
    public static Item getNextColoredPane(){
        int size = BUConfig.get().customOrders.size();
        return CustomOrder.COLORMAP.get(size % 5);
    }

    public static OptionGroup.Builder createOrdersGroup(){
        return OptionGroup.createBuilder()
                .name(Component.literal("Buy Amount Options"))
                .description(OptionDescription.of(Component.literal("Add buttons for custom buy order/insta buy amounts. To add more do /bu customorder add {order amount} {slot number} (top left slot is slot #1, to the right is #2, etc etc.")));
    }

    @EventHandler
    public void replaceItemEvent(ReplaceItemEvent event) {
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        if (!screenInfo.inMenu(ScreenInfo.BazaarMenuType.BUY_ORDER, ScreenInfo.BazaarMenuType.INSTA_BUY) || !isEnabled())
            return;

        if (event.getSlotId() != slotNumber)
            return;

        ItemStack itemStack = new ItemStack(getItem(), 1);
        itemStack.set(BazaarUtils.CUSTOM_SIZE_COMPONENT, String.valueOf(getOrderAmount()));

        itemStack.set(DataComponents.CUSTOM_NAME, Component.literal("Buy " + getOrderAmount()).withStyle(ChatFormatting.DARK_PURPLE));
        event.setReplacement(itemStack);
    }

    @EventHandler
    public void onSlotClicked(SlotClickEvent event) {
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        if (!screenInfo.inMenu(ScreenInfo.BazaarMenuType.BUY_ORDER, ScreenInfo.BazaarMenuType.INSTA_BUY) || !isEnabled())
            return;

        if (event.slot.getContainerSlot() != slotNumber)
            return;
        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        openSignAndInputText();
    }

    public void openSignAndInputText() {
        int signSlotId = 16;
        GUIUtils.clickSlot(signSlotId, 0);
        GUIUtils.runOnNextSignOpen(event -> GUIUtils.setSignText(Integer.toString(getOrderAmount()), true));
    }

    public Option<Boolean> createOption() {
        return super.createBooleanOption(
                "Buy " + getOrderAmount() + " Button",
                "Buy order button for " + getOrderAmount() + " of an item.",
                this::isEnabled,
                this::setEnabled
        );
    }
    public static void buildOptions(OptionGroup.Builder builder){
        List<CustomOrder> customOrders = BUConfig.get().customOrders;
        if(customOrders.isEmpty())
            customOrders.add(new CustomOrder(true, 71680, 17, CustomOrder.COLORMAP.get(0)));

        for (CustomOrder order : customOrders) {
            builder.option(order.createOption());
        }
    }

    public void remove(){
        if (BUConfig.get().customOrders.contains(this)) {
            BUConfig.get().customOrders.remove(this);
            Util.scheduleConfigSave();
            EVENT_BUS.unsubscribe(this);
        }
    }

    @Override
    public void subscribe() {
        EVENT_BUS.subscribe(this);
    }
}