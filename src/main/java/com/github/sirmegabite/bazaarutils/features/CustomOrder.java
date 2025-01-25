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

import java.util.function.Supplier;

public class CustomOrder {
    private final Supplier<Boolean> enabled;
    private final Supplier<Integer> orderAmount;
    private final Supplier<Integer> replaceSlotNumber;
    private final int metaData;
    private boolean signClicked = false;

    public boolean isEnabled() {
        return enabled.get();
    }
    public int getOrderAmount() {
        return orderAmount.get();
    }
    public int getReplaceSlotNumber() {
        return replaceSlotNumber.get();
    }
    public int getMetaData(){
        return metaData;
    }

    public CustomOrder(Supplier<Boolean> enabled, Supplier<Integer> orderAmount,
                       Supplier<Integer> replaceSlotNumber, int metaData) {
        this.enabled = enabled;
        this.orderAmount = orderAmount;
        this.replaceSlotNumber = replaceSlotNumber;
        this.metaData = metaData;
    }


    @SubscribeEvent
    public void onGUI(ReplaceItemEvent event) {
        if (!BazaarUtils.gui.inBuyOrderScreen() || !isEnabled())
            return;

        if (event.getSlotNumber() != getReplaceSlotNumber())
            return;

        ItemStack purpleGlassPane = new ItemStack(Item.getItemFromBlock(Blocks.stained_glass_pane), getOrderAmount(), getMetaData());
        NBTTagCompound displayTag = new NBTTagCompound();
        displayTag.setString("Name", "§dBuy " + getOrderAmount());

        NBTTagList loreList = new NBTTagList();
        NBTTagCompound loreTag = new NBTTagCompound();
        loreTag.setString("Lore", "§7Buy " + getOrderAmount());
        loreList.appendTag(loreTag);

        displayTag.setTag("Lore", loreList);
        purpleGlassPane.setTagCompound(new NBTTagCompound());
        purpleGlassPane.getTagCompound().setTag("display", displayTag);

        event.replaceWith(purpleGlassPane);
    }

    @SubscribeEvent
    public void onSlotClicked(SlotClickEvent event) {
        if (!BazaarUtils.gui.inBuyOrderScreen() || !isEnabled())
            return;

        if (event.slot.slotNumber != getReplaceSlotNumber())
            return;

        openSign(event.slotId);
    }

    @SubscribeEvent
    public void onSignOpened(SignOpenEvent event) {
        if (!signClicked) return;

        GUIUtils.addToSign(Integer.toString(getOrderAmount()), Minecraft.getMinecraft().currentScreen);
        GUIUtils.closeGui();
        signClicked = false;
    }

    public void openSign(int slotId) {
        int signSlotId = slotId - 1;
        GUIUtils.clickItem(signSlotId);
        signClicked = true;
    }
}

