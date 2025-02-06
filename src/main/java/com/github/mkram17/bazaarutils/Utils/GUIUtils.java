package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

//TODO make inBazaar() work
public class GUIUtils {


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
    public boolean inBazaar(){
        return false;
//        if(containerName == null) return false;
//        return inBuyOrderScreen() || inFlipGui || containerName.contains("Bazaar");
    }
    private GenericContainerScreen chestScreen;
    private String containerName;
    private guiTypes guiType;
    private  List<ItemStack> itemStacks = new ArrayList<>();
    public static boolean inFlipGui;

    public enum guiTypes {CHEST, SIGN}
    public void register(){

    }
    public void registerScreenEvent(){
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            BazaarUtils.gui = this;
            if (screen instanceof GenericContainerScreen genericContainerScreen) {
                containerName = Util.removeFormatting(genericContainerScreen.getTitle().getString());
                Util.notifyAll("Container Name: " + containerName, Util.notificationTypes.GUI);

            }
            updateFlipGui();
        });
    }
    @EventHandler(priority = EventPriority.HIGH)
    private void loadSign(SignOpenEvent e){
        guiType = guiType.SIGN;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onChestLoaded(ChestLoadedEvent e){
        guiType = guiType.CHEST;
        itemStacks = e.getItemStacks();
    }

    public static void closeGui(){
        MinecraftClient.getInstance().execute(() -> {
            Util.notifyAll("Closing gui", Util.notificationTypes.GUI);
            MinecraftClient.getInstance().setScreen(null);
        });
    }

    public static void setSignText(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof SignEditScreen screen) {
            AccessorSignEditScreen signScreen = (AccessorSignEditScreen) screen;
            String[] lines = text.split("\n", 4);

            // Save original row to restore later
            int originalRow = signScreen.getCurrentRow();

            // Update all 4 lines
            for (int i = 0; i < 4; i++) {
                String line = i < lines.length ? lines[i] : "";
                signScreen.setCurrentRow(i); // Set the target row
                signScreen.callSetCurrentRowMessage(line); // Update the line
            }

            // Restore original row
            signScreen.setCurrentRow(originalRow);
        }
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
//            Util.notifyAll("Flip gui removed", Util.notificationTypes.GUI);
        }
    }
    public static void clickSlot(int slotIndex, int button) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ClientPlayerEntity player = client.player;

        if (interactionManager == null || player == null) return;

        ScreenHandler screenHandler = player.currentScreenHandler;
        int syncId = screenHandler.syncId;
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(30);
                // Use the interaction manager to handle the click
                interactionManager.clickSlot(
                        syncId,       // Sync ID of the screen handler
                        slotIndex,    // Slot index to click
                        button,       // Mouse button (0 = left, 1 = right)
                        SlotActionType.PICKUP,   // Slot action type (e.g., PICKUP, QUICK_MOVE)
                        player        // The player performing the action
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void sendCommand(String command){
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

}
