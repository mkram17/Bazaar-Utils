package com.github.sirmegabite.bazaarutils.features;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.EventHandlers.ReplaceItemEvent;
import com.github.sirmegabite.bazaarutils.EventHandlers.SignOpenEvent;
import com.github.sirmegabite.bazaarutils.EventHandlers.SlotClickEvent;
import com.github.sirmegabite.bazaarutils.Utils.GUIUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class CustomOrder {
    private final int orderAmount;
    private final int replaceSlotNumber;
    public boolean enabled;
    private boolean signClicked = false;

    public CustomOrder(boolean enabled, int orderAmount, int replaceSlotNumber){
        this.enabled = enabled;
        this.orderAmount = orderAmount;
        this.replaceSlotNumber = replaceSlotNumber;
    }
    @SubscribeEvent
    public void onGUI(ReplaceItemEvent event){
        if(!BazaarUtils.gui.inBuyOrderScreen() || !enabled)
            return;
        if(event.getSlotNumber() != replaceSlotNumber)
            return;

        ItemStack purpleGlassPane = new ItemStack(Item.getItemFromBlock(Blocks.stained_glass_pane), 1, 10);
        NBTTagCompound displayTag = new NBTTagCompound();
        displayTag.setString("Name", "§dBuy " + orderAmount);

        NBTTagList loreList = new NBTTagList();
        NBTTagCompound loreTag = new NBTTagCompound();
        loreTag.setString("Lore", "§7Buy " + orderAmount);
        loreList.appendTag(loreTag);

        displayTag.setTag("Lore", loreList);
        purpleGlassPane.setTagCompound(new NBTTagCompound());
        purpleGlassPane.getTagCompound().setTag("display", displayTag);

        event.replaceWith(purpleGlassPane);
    }

    @SubscribeEvent
    public void onSlotClicked(SlotClickEvent event){
        if(!BazaarUtils.gui.inBuyOrderScreen() || !enabled)
            return;
        if(event.slot.slotNumber != replaceSlotNumber)
            return;
        openSign(event.slotId);
    }

    @SubscribeEvent
    public void onSignOpened(SignOpenEvent event){
        if(!(signClicked)) return;
        GUIUtils.addToSign(Integer.toString(orderAmount), Minecraft.getMinecraft().currentScreen);
        GUIUtils.closeGui();
        signClicked = false;
    }


    public void openSign(int slotId) {
        int signSlotId = slotId - 1;
        GUIUtils.clickItem(signSlotId);
        signClicked = true;
    }
}
