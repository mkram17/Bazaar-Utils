package com.github.mkram17.bazaarutils.features.autoflipper;


import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import com.github.mkram17.bazaarutils.Utils.CustomItemButton;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Binding;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.StateManager;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

//TODO detect in order screen if order isnt full (can use presence of cancel order block)
public class AutoFlipper extends CustomItemButton {
    public static double flipPrice;
    private static double orderPrice = -1;
    private static int orderVolumeFilled = -1;
    private static ItemData item;
    private static boolean shouldAddToSign = false;
    @Getter
    private AutoFlipperSettings settings;
    private transient StateManager<Boolean> enabledStateManager;
    private static boolean orderNotFilled = false;

    public AutoFlipper(AutoFlipperSettings settings) {
        this.settings = settings;
        initializeStateManager();
    }

    @EventHandler
    public void guiChestOpenedEvent(ChestLoadedEvent e) {
        if(enabledStateManager.get() && BazaarUtils.gui.inFlipGui()) {
            item = getFlipItem(e);

            flipPrice = item.getFlipPrice();
        }
    }

    public static void autoAddToSign() {
        if(item != null && flipPrice != 0 && BazaarUtils.gui.wasLastChestFlip()) {
            GUIUtils.setSignText(Double.toString(Util.getPrettyNumber(flipPrice)));
            GUIUtils.closeGui();
            item.flipItem(flipPrice);
        }
    }

    public static ItemData getFlipItem(ChestLoadedEvent e){

        for (ItemStack itemStack : e.getItemStacks()) {

            if (itemStack == null) continue;

            String displayName = itemStack.getName().getString();
            LoreComponent lore = itemStack.getComponents().get(DataComponentTypes.LORE);

            if(displayName.contains("Flip Order")) {
                getItemInfo(lore);

                //method updates item var
                if (matchFound()) {
                    return item;
                }
            }
        }
        return null;
    }

    public static void getItemInfo(LoreComponent lore) {
        //get order volume and price of item that is being flipped
        try {
            String orderPrice = lore.lines().get(3).getSiblings().get(1).getString();
            orderPrice = orderPrice.substring(0, orderPrice.indexOf(" coins"));
            AutoFlipper.orderPrice = Double.parseDouble(orderPrice);
            String orderVolume = lore.lines().get(1).getSiblings().get(1).getString();
            AutoFlipper.orderVolumeFilled = Integer.parseInt(orderVolume);

        } catch (Exception ex) {
            Util.notifyAll("Error while trying to find order price or volume ");
            ex.printStackTrace();
        }
    }

    public static boolean matchFound() {
        item = ItemData.findItem(null, orderPrice, orderVolumeFilled, ItemData.priceTypes.INSTASELL);
        if (item != null) {
            if (item.getStatus() == ItemData.statuses.FILLED) {
                Util.notifyAll("Found match.", Util.notificationTypes.ITEMDATA);
                return true;
            }else {
                Util.notifyAll("found match, but isnt filled", Util.notificationTypes.ERROR);
                return true;
            }
        }
        Util.notifyAll("Couldnt find a match", Util.notificationTypes.ITEMDATA);
        return false;
    }

    @Override @EventHandler
    public void onSlotClicked(SlotClickEvent event) {
        if (!BazaarUtils.gui.inFlipGui() || !enabledStateManager.get() || event.slot.getIndex() != settings.getSlotNumber())
            return;
        GUIUtils.clickSlot(15,0);
        if (item != null)
            shouldAddToSign = true;
    }
    @EventHandler
    private void onSignOpen(SignOpenEvent e){
        if(!shouldAddToSign) return;
        autoAddToSign();
        shouldAddToSign = false;
    }

    @Override @EventHandler
    public void onGUI(ReplaceItemEvent event) {
        if(!BazaarUtils.gui.inFlipGui() || !(event.getSlotId() == settings.getSlotNumber()) || !enabledStateManager.get()) return;
        ItemStack itemStack = new ItemStack(settings.getReplaceItem(), 1);
            itemStack.set(BazaarUtils.CLICK_COUNT_COMPONENT, "FLIP");
        if(item != null)
            itemStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Auto Flip for " + Util.getPrettyNumber(flipPrice) + " coins").formatted(Formatting.DARK_PURPLE));
        else
            itemStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Could not find order").formatted(Formatting.DARK_PURPLE));
        event.setReplacement(itemStack);
    }

    @Override
    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Auto Flipper"))
                .description(OptionDescription.of(Text.literal("Button in flip order menu to undercut market prices for items.")))
                .binding(enabledStateManager.get(),
                        enabledStateManager::get,
                        enabledStateManager::set)
                .controller(BUConfig::createBooleanController)
                .build();
    }

    public void initializeStateManager(){
        Binding<Boolean> settingsBinding = Binding.generic(
                getSettings().isEnabled(),
                settings::isEnabled,
                settings::setEnabled
        );
        enabledStateManager = StateManager.createSimple(settingsBinding);
        enabledStateManager.sync();
    }

}

