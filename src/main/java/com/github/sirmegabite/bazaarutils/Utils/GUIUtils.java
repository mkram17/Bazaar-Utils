package com.github.sirmegabite.bazaarutils.Utils;

import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import com.github.sirmegabite.bazaarutils.features.AutoFlipper;
import com.github.sirmegabite.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiOpenEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

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
        return flipGUI.inFlipGui();
    }

    private GuiOpenEvent event;
    private GuiScreen gui;
    private GuiChest chestScreen;
    private ContainerChest containerChest;
    private String containerName;
    private guiTypes guiType;
    private  List<ItemStack> itemStacks = new ArrayList<>();
    private final List<Runnable> postLoadTasks = new CopyOnWriteArrayList<>();

    public static GUIUtils flipGUI;

    public enum guiTypes {CHEST, SIGN, OTHER, LOADINGCHEST}

    public GUIUtils(GuiOpenEvent event) {
        this.event = event;
        this.gui = event.gui;
        load();
    }

    public void load(){
        if(gui instanceof GuiChest){
            loadChest();
        } else if (gui instanceof AccessorGuiEditSign){
            loadSign();
        } else {
            guiType = guiType.OTHER;
        }
    }

    public void loadChest(){
        guiType = guiType.LOADINGCHEST;
        //gui not loaded at this point, so is null
        Util.notifyAll("In a chest.", Util.notificationTypes.GUI);
        onChestLoaded();
    }

    public void loadSign(){
        guiType = guiType.SIGN;
        Util.notifyAll("In a sign", Util.notificationTypes.GUI);

    }

    public void addPostLoadTask(Runnable task) {
        postLoadTasks.add(task);
    }

    public void onChestLoaded(){
        CompletableFuture.runAsync(this::checkIfGuiLoaded).thenRun(() ->{
            guiType = guiType.CHEST;
            updateItemStacks();
            containerName = this.getContainerChest().getLowerChestInventory().getDisplayName().getFormattedText();
            Util.notifyAll("Container Name: " + this.getContainerName(), Util.notificationTypes.GUI);
            updateFlipGui();
            AutoFlipper.updateFlipData();
            postLoadTasks.forEach(Runnable::run); // Execute registered tasks
        });
    }

    public void checkIfGuiLoaded() {
        while (true) {
            //sometimes gui has not loaded at this point causing errors but wont say anything
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            gui = Minecraft.getMinecraft().currentScreen;
            chestScreen = (GuiChest) gui;
            containerChest = (ContainerChest) chestScreen.inventorySlots;

            updateItemStacks();
            int size = containerChest.getLowerChestInventory().getSizeInventory();

            if (size == 0) {
                continue;
            }

            ItemStack bottomRightItem = containerChest.getLowerChestInventory().getStackInSlot(size - 1);

            if (bottomRightItem != null && areItemsLoaded()) {
                Util.notifyAll("Item detected in the bottom-right corner: " + bottomRightItem.getDisplayName(), Util.notificationTypes.GUI);
                break;
            }
        }
    }

    public boolean areItemsLoaded(){
        for (ItemStack item : itemStacks) {
            NBTTagCompound tagCompound = item.getTagCompound();

            if (tagCompound == null) continue;
            String displayName = Util.removeFormatting(tagCompound.getCompoundTag("display").getString("Name"));
            if(displayName.contains("Loading")) {
                Util.notifyAll("Loading item...", Util.notificationTypes.GUI);
                return false;
            }
        }
        return true;
    }

    public void updateItemStacks() {
        itemStacks.clear();
        for (int i = 0; i < containerChest.getLowerChestInventory().getSizeInventory(); i++) {
            ItemStack stack = containerChest.getLowerChestInventory().getStackInSlot(i);

            if (stack != null) {
//                Util.notifyAll("Slot " + i + ": " + stack, this.getClass());
                itemStacks.add(stack);
            }
        }
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
            flipGUI = this;
            Util.notifyAll("In flip gui", Util.notificationTypes.GUI);
        }
        else if(guiType == guiTypes.CHEST) {
            flipGUI = null;
            Util.notifyAll("Flip gui removed", Util.notificationTypes.GUI);
        }
    }
}
