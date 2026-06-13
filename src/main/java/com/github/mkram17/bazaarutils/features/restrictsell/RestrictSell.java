package com.github.mkram17.bazaarutils.features.restrictsell;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.Util;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//TODO maybe color chest if it is locked
public class RestrictSell implements BUListener {
    public enum restrictBy{PRICE, VOLUME, NAME}
    @Getter @Setter
    private boolean enabled;
    private final int safetyClicksRequired;
    @Getter @Setter
    private ArrayList<RestrictSellControl> controls;
    private static final int SELL_ITEM_SLOT_ID = 47;
    private boolean locked = false;
    @Getter
    private int safetyClicks = 0;

    public void addSafetyClick(){
        safetyClicks++;
    }
    public void resetSafetyClicks(){
        safetyClicks = 0;
    }

    public RestrictSell(boolean enabled, int safetyClicksRequired, ArrayList<RestrictSellControl> controls) {
        this.enabled = enabled;
        this.safetyClicksRequired = safetyClicksRequired;
        this.controls = controls;
    }
    private void registerScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            safetyClicks = 0;
        });
    }

    @EventHandler
    private void onGUI(ReplaceItemEvent e){
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        try {
            if (e.getSlotId() != SELL_ITEM_SLOT_ID || !screenInfo.inBazaar())
                return;
            if (e.getOriginal() == null || e.getOriginal().getComponentsPatch().get(DataComponents.LORE) == null)
                return;
            if (e.getOriginal().getComponentsPatch().get(DataComponents.LORE).get().styledLines().size() < 6 || e.getOriginal().getComponentsPatch().get(DataComponents.LORE).get().styledLines().get(4).getString().contains("Loading"))
                return;

            ItemStack sellButton = e.getOriginal().copy();
            List<Component> changedComponents = sellButton.getComponentsPatch().get(DataComponents.LORE).get().styledLines();

            int numItems = findNumItems(changedComponents);
            ArrayList<SellItem> items = getItems(changedComponents, numItems);
            String coinsText = changedComponents.get(5 + numItems).getString();
            if(!coinsText.contains("coins"))
                return;
            double totalPrice = Double.parseDouble(coinsText.substring(coinsText.indexOf(": ") + 2, coinsText.indexOf(" coins")).replace(",", ""));


            locked = isInstaSellLocked(items, totalPrice);
            if(locked){
                sellButton = e.getOriginal().copy();
                if(safetyClicksRequired != safetyClicks)
                    sellButton.set(BazaarUtils.CUSTOM_SIZE_COMPONENT, String.valueOf(safetyClicksRequired-safetyClicks));
                }
            e.setReplacement(sellButton);
        } catch (Exception ex) {
            Util.notifyError("Error parsing sell item components", ex);
        }
    }

    private int findNumItems(List<Component> changedComponents) {
        //if there are items with no buy orders in inv, you get "Some items can't be sold" and there are 2 extra components
        if(Util.findComponentWith(changedComponents, "Some items can't be sold") == null){
            return changedComponents.size()-8;
        } else {
            return changedComponents.size()-10;
        }
    }
    public ArrayList<SellItem> getItems(List<Component> changedComponents, int numItems){
        ArrayList<SellItem> items = new ArrayList<>();

        try {
            for (int i = 4; i < 4 + numItems; i++) {
                if (i >= changedComponents.size()) {
                    Util.notifyError("Component index " + i + " out of bounds. Total components: " + changedComponents.size(), new Throwable("Restrict Sell Error"));
                    break;
                }

                var components = changedComponents.get(i).getSiblings();
                if(components.isEmpty())
                    continue;

                if (components.size() < 2) {
                    Util.notifyError("Not enough components to find item volume. Size: " + components.size() + " at index " + i, new Throwable("Restrict Sell Error"));
                    continue;
                }
                int indexOfVolume = Util.componentIndexOf(components, "x")-1;
                if(indexOfVolume < 0)
                    continue;
                int volume = Integer.parseInt(components.get(indexOfVolume).getString().replace(",", ""));

                if (components.size() > 3) {
                    String name = components.get(3).getString().trim();
                    SellItem newItem = new SellItem(volume, name);
                    items.add(newItem);
                } else {
                    Util.notifyError("Not enough components to find item name. Size: " + components.size() + " at index " + i, new Throwable("Restrict Sell Error"));
                }
            }
        } catch (Exception e) {
            Util.notifyError("Error parsing sell item components. NumItems: " + numItems + ", Components size: " + changedComponents.size(), e);
        }
        return items;
    }

    public boolean isSlotLocked(int slotId){
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        return screenInfo.inBazaar() && slotId == SELL_ITEM_SLOT_ID && locked;
    }

    private boolean isInstaSellLocked(ArrayList<SellItem> items, double totalPrice){
        return isSellingRestrictedIndividualItem(items) || isSellingRestrictedTotalPrice(totalPrice);
    }

    private boolean isSellingRestrictedTotalPrice(double totalPrice){
        for(RestrictSellControl control : controls){
            if(control.isEnabled()) {
                if (control.getRule() == restrictBy.PRICE && totalPrice > control.getAmount())
                    return true;
            }
        }
        return false;
    }
    private boolean isSellingRestrictedIndividualItem(ArrayList<SellItem> items){
        for(SellItem item : items){
            if(isSellingItemRestricted(item.volume(), item.name()))
                return true;
        }
        return false;
    }
    private boolean isSellingItemRestricted(int volume, String name){
        for(RestrictSellControl control : controls){
            if(control.isEnabled()) {
                if (control.getRule() == restrictBy.VOLUME && volume > control.getAmount())
                    return true;
                else if (control.getRule() == restrictBy.NAME && name.equalsIgnoreCase(control.getName()))
                    return true;
            }
        }
        return false;
    }
    public void addRule(restrictBy newRule, double limit){
        controls.add(new RestrictSellControl(newRule, limit));
    }
    public void addRule(restrictBy newRule, String name){
        controls.add(new RestrictSellControl(newRule, name));
    }

    public String getMessage(){
        StringBuilder message = new StringBuilder("Sell protected by rules:");
        for(RestrictSellControl control : controls) {
            if(!control.isEnabled())
                continue;
            if (control.getRule() == restrictBy.PRICE)
                message.append(" PRICE: ");
            else if(control.getRule() == restrictBy.VOLUME)
                message.append(" VOLUME: ");
            else {
                message.append(" NAME: ");
                message.append(control.getName());
                continue;
            }
            message.append(control.getAmount());
        }
        message.append(" (Safety Clicks Left: ").append(3 - safetyClicks).append(")");
        return message.toString();
    }

    public Option<Boolean> createRuleOption(RestrictSellControl control) {
        // Determine display text based on rule type
        Component nameText;
        Component descriptionText;

        if (control.getRule() == restrictBy.NAME) {
            String itemName = control.getName(); // Assuming getName() exists for NAME rules
            nameText = Component.literal("Item: " + itemName);
            descriptionText = Component.literal("Block insta-sell for item: " + itemName);
        } else {
            double amount = control.getAmount();
            String typeText = control.getRule() == restrictBy.VOLUME ? "Volume < " : "Price < ";
            nameText = Component.literal(typeText + amount);
            String desc = control.getRule() == restrictBy.PRICE ?
                    "Block insta-sell if price exceeds " + amount :
                    "Block insta-sell if volume exceeds " + amount;
            descriptionText = Component.literal(desc);
        }

        return Option.<Boolean>createBuilder()
                .name(nameText)
                .description(OptionDescription.of(descriptionText))
                .binding(
                        false,
                        control::isEnabled,
                        control::setEnabled
                )
                .controller(BUConfigGui::createBooleanController)
                .build();
    }

    public void buildOptions(OptionGroup.Builder builder){
        for(RestrictSellControl control : getControls()){
            builder.option(createRuleOption(control));
        }
    }
    @Override
    public void subscribe() {
        registerScreenEvent();
        EVENT_BUS.subscribe(this);
    }
}
