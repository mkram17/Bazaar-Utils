package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        if(containerName == null) return false;
        return (containerName.contains("How many do you want?") || containerName.contains("Order options") || containerName.contains("Bazaar"));
    }
    private GenericContainerScreen chestScreen;
    private String containerName;
    private guiTypes guiType;
    private  List<ItemStack> itemStacks = new ArrayList<>();
    public static boolean inFlipGui;

    public enum guiTypes {CHEST, SIGN}
    public void register(){

    }
    public void registerGui(){
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            BazaarUtils.gui = this;
            if (screen instanceof GenericContainerScreen genericContainerScreen) {
                containerName = Util.removeFormatting(genericContainerScreen.getTitle().getString());
                Util.notifyAll("Container Name: " + containerName, Util.notificationTypes.GUI);

            }
            updateFlipGui();
        });
    }
    @EventHandler
    public void loadSign(SignOpenEvent e){
        guiType = guiType.SIGN;
    }

    @EventHandler
    public void onChestLoaded(ChestLoadedEvent e){
        guiType = guiType.CHEST;
        itemStacks = e.getItemStacks();
    }

    public static void closeGui(){
        CompletableFuture.runAsync(() -> {
            try{
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Util.notifyAll("Closing gui", Util.notificationTypes.GUI);
            MinecraftClient.getInstance().setScreen(null);
        });
    }

    public static void setSignText(String text, boolean front) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof SignEditScreen screen) {
            SignBlockEntity sign = ((AccessorSignEditScreen) screen).getBlockEntity();

            // Get the current SignText to modify
            SignText signText = front ? sign.getFrontText() : sign.getBackText();

            // Split text into lines
            String[] lines = text.split("\n", 4);

            // Update each line of the sign
            for (int i = 0; i < 4; i++) {
                String lineContent = i < lines.length ? lines[i] : "";
                Text lineText = Text.literal(lineContent);
                signText = signText.withMessage(i, lineText);
            }

            sign.setText(signText, front);

            closeGui();
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
    public void clickSlot(int slotIndex, int button) {
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

    public static void openBazaar(){
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.sendMessage(Text.of("/bz"), false);
    }
}
