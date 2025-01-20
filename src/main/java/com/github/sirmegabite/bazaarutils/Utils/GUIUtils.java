package com.github.sirmegabite.bazaarutils.Utils;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.github.sirmegabite.bazaarutils.EventHandlers.ChestLoadedEvent;
import com.github.sirmegabite.bazaarutils.features.AutoFlipper;
import com.github.sirmegabite.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class GUIUtils {


    public GuiOpenEvent getEvent() {
        return event;
    }

    public GuiScreen getGui() {
        return gui;
    }

    public GuiChest getChestScreen() {
        return chestScreen;
    }

    public ContainerChest getContainerChest() {
        return containerChest;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public guiTypes getGuiType() {
        return guiType;
    }

    public void setGuiType(guiTypes guiType) {
        this.guiType = guiType;
    }

    public List<ItemStack> getItemStacks() {
        return itemStacks;
    }

    public void setItemStacks(List<ItemStack> itemStacks) {
        this.itemStacks = itemStacks;
    }


    public boolean wasLastChestFlip(){
        return inFlipGui;
    }

    private GuiOpenEvent event;
    private GuiScreen gui;
    private GuiChest chestScreen;
    private ContainerChest containerChest;
    private String containerName;
    private guiTypes guiType;
    private  List<ItemStack> itemStacks = new ArrayList<>();
    public static boolean inFlipGui;

    public enum guiTypes {CHEST, SIGN}

    @SubscribeEvent
    public void load(GuiOpenEvent e){
        BazaarUtils.gui = this;
    }
    @SubscribeEvent
    public void loadSign(GuiOpenEvent e){
        if(!(e.gui instanceof AccessorGuiEditSign))
            return;
        guiType = guiType.SIGN;
        Util.notifyAll("In a sign", Util.notificationTypes.GUI);
    }

    @SubscribeEvent
    public void onChestLoaded(ChestLoadedEvent e){
        guiType = guiType.CHEST;
        Util.notifyAll("In a chest.", Util.notificationTypes.GUI);
        itemStacks = e.getItemStacks();
        containerName = e.getContainerName();
        Util.notifyAll("Container Name: " + this.getContainerName(), Util.notificationTypes.GUI);
        updateFlipGui();
        AutoFlipper.updateFlipData();
    }

    public static void closeGui(){
        try{
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Util.notifyAll("Closing gui", Util.notificationTypes.GUI);
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    //thanks to SkyHanni
    public static void addToSign(String info, GuiScreen thisGui){
        IChatComponent[] lines = ((AccessorGuiEditSign) thisGui).getTileSign().signText;
        int index = ((AccessorGuiEditSign) thisGui).getEditLine();
        String text = lines[index].getUnformattedText() + info;
        lines[index] = new ChatComponentText(Util.capAtMinecraftLength(text,91));
    }

    public boolean inFlipGui(){
        if(containerName == null) return false;
        return containerName.contains("Order options");
    }

    public void updateFlipGui(){
        if(inFlipGui()) {
            inFlipGui = true;
            Util.notifyAll("In flip gui", Util.notificationTypes.GUI);
        }
        else if(guiType == guiTypes.CHEST) {
            inFlipGui = false;
            Util.notifyAll("Flip gui removed", Util.notificationTypes.GUI);
        }
    }
}
