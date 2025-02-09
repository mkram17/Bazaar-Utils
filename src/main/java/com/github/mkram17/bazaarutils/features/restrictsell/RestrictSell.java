package com.github.mkram17.bazaarutils.features.restrictsell;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

//TODO maybe color chest if it is locked
//TODO make it work with multiple types of item being sold
public class RestrictSell {
    public enum restrictBy{PRICE, VOLUME}
    @Getter @Setter
    private boolean enabled;
    private int safetyClicks;
    @Getter @Setter
    private ArrayList<RestrictSellControl> controls;
    private static int SELLITEMID = 47;
    private boolean locked = false;

    public RestrictSell(boolean enabled, int safetyClicks, ArrayList<RestrictSellControl> controls) {
        this.enabled = enabled;
        this.safetyClicks = safetyClicks;
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
            String coinsText = changedComponents.get(4).getSiblings().get(5).getString();
            double price = Double.parseDouble(coinsText.substring(0, coinsText.indexOf(" coins")).replace(",", ""));
            int volume = Integer.parseInt(changedComponents.get(4).getSiblings().get(1).getString().replace(",", ""));
            locked = isLocked(price, volume);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public boolean isSlotLocked(int slotId){
        return BazaarUtils.gui.inBazaar() && slotId == SELLITEMID && locked;
    }

    private boolean isLocked(double price, int volume){
        for(RestrictSellControl control : controls){
            if(control.isEnabled()) {
                if (control.getRestriction() == restrictBy.PRICE && price > control.getAmount()) {
                    return true;
                } else if (control.getRestriction() == restrictBy.VOLUME && volume > control.getAmount())
                    return true;
            }
        }
        return false;
    }
    public void addRestriction(restrictBy newRestriction, double limit){
        controls.add(new RestrictSellControl(newRestriction, limit));
    }

    public String getMessage(){
        String message = "Sell protected by rules:";
        for(RestrictSellControl control : controls) {
            if (control.getRestriction() == restrictBy.PRICE)
                message += " PRICE: ";
            else
                message += " VOLUME: ";
            message += control.getAmount();
        }
        return message;
    }

    public Option<Boolean> createRestrictionOption(RestrictSellControl control) {
        return Option.<Boolean>createBuilder()
                .name(Text.literal((control.getRestriction() == restrictBy.PRICE ? "Price < ": "Restrict volume to below ") + control.getAmount()))
                .description(OptionDescription.of(Text.literal((control.getRestriction() == restrictBy.PRICE ? "Will not allow you insta sell if the price is greater than " : "Will not allow you insta sell if the volume is greater than ") + control.getAmount())))
                .binding(false,
                        control::isEnabled,
                        control::setEnabled)
                .controller(BUConfig::createBooleanController)
                .build();
    }
    
}
