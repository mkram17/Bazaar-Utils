package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.EventHandlers.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.EventHandlers.SignOpenEvent;
import com.github.mkram17.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    public boolean inBuyOrderScreen(){
        if(containerName == null) return false;
        return containerName.contains("How many do you want?");
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
    public void onGui(GuiOpenEvent e){
        BazaarUtils.gui = this;
        inFlipGui = false;
        if (e.gui instanceof GuiChest) {
            containerName = Util.removeFormatting(((ContainerChest) ((GuiChest) e.gui).inventorySlots).getLowerChestInventory().getDisplayName().getFormattedText());
            Util.notifyAll("Container Name: " + containerName, Util.notificationTypes.GUI);

        }
        updateFlipGui();
    }
    @SubscribeEvent
    public void loadSign(SignOpenEvent e){
        guiType = guiType.SIGN;
    }

    @SubscribeEvent
    public void onChestLoaded(ChestLoadedEvent e){
        guiType = guiType.CHEST;
        itemStacks = e.getItemStacks();
    }

    public static void closeGui(){
        CompletableFuture.runAsync(() -> {
            try{
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Util.notifyAll("Closing gui", Util.notificationTypes.GUI);
            Minecraft.getMinecraft().displayGuiScreen(null);
        });
    }

    //thanks to SkyHanni
    public static void addToSign(String info, GuiScreen thisGui){
        if(!(thisGui instanceof AccessorGuiEditSign)) {
            Util.notifyAll("Cannot add to sign. GUI not recognized as sign", Util.notificationTypes.ERROR);
            return;
        }
        IChatComponent[] lines = ((AccessorGuiEditSign) thisGui).getTileSign().signText;
        int index = ((AccessorGuiEditSign) thisGui).getEditLine();
        String text = lines[index].getUnformattedText() + info;
        lines[index] = new ChatComponentText(Util.capAtMinecraftLength(text,91));
    }

    public boolean inFlipGui(){
        if(containerName == null || Minecraft.getMinecraft().currentScreen == null) return false;
        return containerName.contains("Order options");
    }

    public void updateFlipGui(){
        if(inFlipGui()) {
            inFlipGui = true;
            Util.notifyAll("In flip gui", Util.notificationTypes.GUI);
        }
        else if(guiType == guiTypes.CHEST) {
            inFlipGui = false;
//            Util.notifyAll("Flip gui removed", Util.notificationTypes.GUI);
        }
    }

    public static void clickItem(int itemId){
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        Container openContainer = player.openContainer;
        // Validate FIRST
        if (openContainer == null) {
            Util.notifyAll("No open container!", Util.notificationTypes.ERROR);
            return;
        }

        // Validate slotId range (0-26 for chest slots)
        if (itemId < 0 || itemId >= openContainer.inventorySlots.size()) {
            Util.notifyAll("Invalid slot: " + itemId, Util.notificationTypes.ERROR);
            return;
        }

        // Now safe to access slots
        CompletableFuture.runAsync(() -> {
            try{
                Thread.sleep(30);
                Minecraft.getMinecraft().playerController.windowClick(
                        openContainer.windowId,
                        itemId,
                        0,          // Left click
                        0,          // Normal click type
                        player
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
