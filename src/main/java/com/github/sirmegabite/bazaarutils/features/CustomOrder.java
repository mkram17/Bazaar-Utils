package com.github.sirmegabite.bazaarutils.features;

import com.github.sirmegabite.bazaarutils.EventHandlers.ReplaceItemEvent;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class CustomOrder {
    private final int orderAmount;
    private final int replaceSlotNumber;
    public CustomOrder(int orderAmount, int replaceSlotNumber){
        this.orderAmount = orderAmount;
        this.replaceSlotNumber = replaceSlotNumber;
    }
    @SubscribeEvent
    public void onGUI(ReplaceItemEvent event){
        if(!(event.getInventory().getDisplayName().equals("Order options")))
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
}
