package com.github.mkram17.bazaarutils.features.restrictsell;

import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.features.restrictsell.controls.DoubleSellRestrictionControl;
import com.github.mkram17.bazaarutils.features.restrictsell.controls.SellRestrictionControl;
import com.github.mkram17.bazaarutils.features.restrictsell.controls.StringSellRestrictionControl;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.InstaSellUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//TODO maybe color chest if it is locked
public class InstaSellRestrictions implements BUListener {
    public enum restrictBy{PRICE, VOLUME, NAME}
    @Getter @Setter
    private boolean enabled;
    private int safetyClicksRequired;
    @Getter @Setter
    private ArrayList<SellRestrictionControl> controls;
    @Getter
    private int safetyClicks = 0;
    private boolean blockClicks;

    private static final int INSTA_SELL_SLOT_INDEX = 47;

    public InstaSellRestrictions(boolean enabled, int safetyClicksRequired, ArrayList<SellRestrictionControl> controls) {
        this.enabled = enabled;
        this.safetyClicksRequired = safetyClicksRequired;
        this.controls = controls;
    }
    private void registerScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            safetyClicks = 0;
            blockClicks = true;
        });
    }
    @EventHandler
    private void onChestLoaded(ChestLoadedEvent e) {
        if (!enabled || !ScreenInfo.getCurrentScreenInfo().inMenu(ScreenInfo.BazaarMenuType.BAZAAR_MAIN_PAGE))
            return;

        List<OrderInfoContainer> items = InstaSellUtil.getInstaSellOrders(e.getItemStacks());
        blockClicks = shouldRestrictInstaSell(items);
    }

    @EventHandler
    private void onClick(SlotClickEvent clickEvent){
        if(!enabled || !blockClicks || clickEvent.slot.getIndex() != INSTA_SELL_SLOT_INDEX)
            return;
        if (safetyClicks < safetyClicksRequired) {
            safetyClicks++;
            PlayerActionUtil.notifyAll(getMessage());
            clickEvent.cancel();
        }
    }

    private boolean shouldRestrictInstaSell(List<OrderInfoContainer> items){
        return items.stream().anyMatch(this::isItemRestricted);
    }

    public void addRule(SellRestrictionControl control){
        controls.add(control);
    }

    private boolean isItemRestricted(OrderInfoContainer item) {
        for(SellRestrictionControl control : controls) {
            if(!control.isEnabled())
                continue;
            if (control.shouldRestrict(item)) {
                return true;
            }
        }
        return false;
    }

    public String getMessage(){
        StringBuilder message = new StringBuilder("Sell protected by rules:");
        for(SellRestrictionControl control : controls) {
            if(!control.isEnabled())
                continue;
            if(control instanceof DoubleSellRestrictionControl doubleControl) {
                if (control.getRule() == restrictBy.PRICE)
                    message.append(" PRICE: ");
                else if (control.getRule() == restrictBy.VOLUME)
                    message.append(" VOLUME: ");
                message.append(doubleControl.getAmount());
            } else {
                StringSellRestrictionControl stringControl = (StringSellRestrictionControl) control;
                message.append(" NAME: ");
                message.append(stringControl.getName());
            }
        }
        message.append(" (Safety Clicks Left: ").append(3 - safetyClicks).append(")");
        return message.toString();
    }

    public Option<Boolean> createRuleOption(SellRestrictionControl control) {
        // Determine display text based on rule type
        Text nameText;
        Text descriptionText;

        if (control instanceof StringSellRestrictionControl stringControl) {
            String itemName = stringControl.getName(); // Assuming getName() exists for NAME rules
            nameText = Text.literal("Item: " + itemName);
            descriptionText = Text.literal("Block insta-sell for item: " + itemName);
        } else if (control instanceof DoubleSellRestrictionControl doubleControl){
            double amount = doubleControl.getAmount();
            String typeText = control.getRule() == restrictBy.VOLUME ? "Volume < " : "Price < ";
            nameText = Text.literal(typeText + amount);
            String desc = control.getRule() == restrictBy.PRICE ?
                    "Block insta-sell if price exceeds " + amount :
                    "Block insta-sell if volume exceeds " + amount;
            descriptionText = Text.literal(desc);
        } else {
            nameText = Text.literal("Unknown Rule");
            descriptionText = Text.literal("This rule is of an unknown type.");
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
        for(SellRestrictionControl control : getControls()){
            builder.option(createRuleOption(control));
        }
    }
    @Override
    public void subscribe() {
        registerScreenEvent();
        EVENT_BUS.subscribe(this);
    }
}
