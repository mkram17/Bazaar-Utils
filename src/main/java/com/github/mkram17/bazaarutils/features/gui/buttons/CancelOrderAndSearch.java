package com.github.mkram17.bazaarutils.features.gui.buttons;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

@ConfigObject
public class CancelOrderAndSearch extends BUListener {

    public int getSlotIndex() {
        return ButtonsConfig.CANCEL_ORDER_AND_SEARCH.slotIndex;
    }

    private static final ItemStackTemplate ITEM_TEPLATE = new ItemStackTemplate(Items.BLUE_TERRACOTTA);

    public ItemRef getItemRef() {
        return ItemRef.of(ITEM_TEPLATE);
    }

    @Getter
    private transient ItemStack replacementItem;

    public CancelOrderAndSearch() {
        this.replacementItem = Items.BLUE_TERRACOTTA.getDefaultInstance();
    }

    private transient OrderInfo orderInfo;

    private Boolean inCorrectScreen; // access boolean using isInCorrectScreen()

//        !! see comment at constructor
//    private boolean isInCorrectScreen() {
//        if(inCorrectScreen == null){
//            var screen = ScreenInfo.getCurrentScreenInfo();
//            return screen.inMenu(ScreenInfo.BazaarMenuType.CANCEL_ORDER);
//        } else {
//            return inCorrectScreen;
//        }
//
//        return inCorrectScreen;
//    }
//
//    @EventHandler
//    private void replaceItem(ReplaceItemEvent event) {
//        if(!isInCorrectScreen() || !shouldReplaceItem(event)) return;
//        event.setReplacement(super.replacementItem);
//    }
//
//    @EventHandler
//    private void onClick(SlotClickEvent event) {
//        if(!isInCorrectScreen() || !wasButtonSlotClicked(event)) return;
//        GUIUtils.closeHandledScreen();
//        PlayerActionUtil.runCommand("bz " + orderInfo.getName());
//    }

}
