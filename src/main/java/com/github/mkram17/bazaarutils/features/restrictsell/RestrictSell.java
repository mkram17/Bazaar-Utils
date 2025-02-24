package com.github.mkram17.bazaarutils.features.restrictsell;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

//TODO maybe color chest if it is locked
//TODO use custom size for safety clicks left
public class RestrictSell {
    public enum restrictBy{PRICE, VOLUME, NAME}
    @Getter @Setter
    private boolean enabled;
    private int safetyClicksRequired;
    @Getter @Setter
    private ArrayList<RestrictSellControl> controls;
    private static int SELLITEMID = 47;
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

    @EventHandler
    private void onGUI(ReplaceItemEvent e){
        try {
            if (e.getSlotId() != SELLITEMID || !BazaarUtils.gui.inBazaar())
                return;
            if (e.getOriginal() == null || e.getOriginal().getComponentChanges().get(DataComponentTypes.LORE) == null)
                return;
            if (e.getOriginal().getComponentChanges().get(DataComponentTypes.LORE).get().styledLines().size() < 6 || e.getOriginal().getComponentChanges().get(DataComponentTypes.LORE).get().styledLines().get(4).getString().contains("Loading"))
                return;
            ItemStack sellButton = e.getOriginal();
            List<Text> changedComponents = sellButton.getComponentChanges().get(DataComponentTypes.LORE).get().styledLines();
            int numItems = changedComponents.size()-8;
            ArrayList<SellItem> items = getItems(changedComponents, numItems);
            String coinsText = changedComponents.get(5 + numItems).getString();
            double totalPrice = Double.parseDouble(coinsText.substring(coinsText.indexOf(": ") + 2, coinsText.indexOf(" coins")).replace(",", ""));

            locked = isLocked(items, totalPrice);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public ArrayList<SellItem> getItems(List<Text> changedComponents, int numItems){
        ArrayList<SellItem> items = new ArrayList<>();

        for(int i = 4; i<4+numItems; i++){
            var component = changedComponents.get(i).getSiblings();
            int volume = Integer.parseInt(component.get(1).getString().replace(",", ""));
            String name = component.get(3).getString().trim();

            SellItem newItem = new SellItem(volume, name);
            items.add(newItem);
        }

        return items;
    }
    public boolean isSlotLocked(int slotId){
        return BazaarUtils.gui.inBazaar() && slotId == SELLITEMID && locked;
    }

    private boolean isLocked(ArrayList<SellItem> items, double totalPrice){
        for(RestrictSellControl control : controls){
            if(control.isEnabled()) {
                if (control.getRule() == restrictBy.PRICE && totalPrice > control.getAmount())
                    return true;
            }
        }
        for(SellItem item : items){
            if(isItemRestricted(item.getVolume(), item.getName()))
                return true;
        }
        return false;
    }
    private boolean isItemRestricted(int volume, String name){
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
        String message = "Sell protected by rules:";
        for(RestrictSellControl control : controls) {
            if(!control.isEnabled())
                continue;
            if (control.getRule() == restrictBy.PRICE)
                message += " PRICE: ";
            else if(control.getRule() == restrictBy.VOLUME)
                message += " VOLUME: ";
            else {
                message += " NAME: ";
                message += control.getName();
                continue;
            }
            message += control.getAmount();
        }
        message += " (Safety Clicks Left: " + (3-safetyClicks) + ")";
        return message;
    }

    public Option<Boolean> createRuleOption(RestrictSellControl control) {
        // Determine display text based on rule type
        Text nameText;
        Text descriptionText;

        if (control.getRule() == restrictBy.NAME) {
            String itemName = control.getName(); // Assuming getName() exists for NAME rules
            nameText = Text.literal("Item: " + itemName);
            descriptionText = Text.literal("Block insta-sell for item: " + itemName);
        } else {
            double amount = control.getAmount();
            String typeText = control.getRule() == restrictBy.VOLUME ? "Volume < " : "Price < ";
            nameText = Text.literal(typeText + amount);
            String desc = control.getRule() == restrictBy.PRICE ?
                    "Block insta-sell if price exceeds " + amount :
                    "Block insta-sell if volume exceeds " + amount;
            descriptionText = Text.literal(desc);
        }

        return Option.<Boolean>createBuilder()
                .name(nameText)
                .description(OptionDescription.of(descriptionText))
                .binding(
                        false,
                        control::isEnabled,
                        control::setEnabled
                )
                .controller(BUConfig::createBooleanController)
                .build();
    }

    public void buildOptions(OptionGroup.Builder builder){
        for(RestrictSellControl control : getControls()){
            builder.option(createRuleOption(control));
        }
    }
}
