package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.SignOpenEvent;
import com.github.mkram17.bazaarutils.features.Bookmark;
import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.github.mkram17.bazaarutils.BazaarUtils.eventBus;

//TODO make inBazaar() work all the time
public class GUIUtils implements BUListener {
    public static boolean wasLastChestFlip(){
        return inFlipGui;
    }

    public static boolean inBuyOrderScreen(){
        if(getContainerName() == null) return false;
        return getContainerName().contains("How many do you want?");
    }
    public static boolean inInstaBuy(){
        if(getContainerName() == null) return false;
        return getContainerName().contains("➜ Insta");
    }
    public static boolean inOrderScreen(){
        if(getContainerName() == null) return false;
        return getContainerName().contains("Bazaar Orders");
    }

    public static boolean inBazaar(){
        if(getContainerName() == null) return false;
        return inBuyOrderScreen() || inFlipGui || inInstaBuy() || getContainerName().contains("Bazaar") || inOrderScreen() || getContainerName().contains("➜");
    }

    //only for specific items
    public static boolean inAnyItemScreen(){
        if(getContainerName() == null || getContainerName().contains("Bazaar")) return false;
        return getContainerName().contains("➜")
                || inBuyOrderScreen()
                || inInstaBuy();
    }
    @Getter @Setter
    private static guiTypes guiType;
    public static boolean inFlipGui;
    @Getter @Setter
    private static Inventory lowerChestInventory;
    @Getter @Setter
    private static Bookmark currentBookmark;
    @Getter @Setter
    private static Screen previousScreen;
    @Getter @Setter
    private static String previousScreenName;

    @Override
    public void subscribe() {
        eventBus.subscribe(this);
        registerScreenEvent();
    }

    public enum guiTypes {CHEST, SIGN}


    public static String getContainerName(){
        var screen = MinecraftClient.getInstance().currentScreen;
        if(screen != null)
            return Util.removeFormatting(screen.getTitle().getString());
        return null;
    }

    public static void registerScreenEvent(){
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            lowerChestInventory = null;
        });

        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            if(client.currentScreen != null)
                previousScreen = client.currentScreen;
            previousScreenName = getContainerName();
        });
    }

    public static ScreenHandler getHandledScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return null;
        return client.player.currentScreenHandler;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private static void loadSign(SignOpenEvent e){
        guiType = guiType.SIGN;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private static void onChestLoaded(ChestLoadedEvent e){
        guiType = guiType.CHEST;

        currentBookmark = null;
        if(!Bookmark.inItemScreen())
            return;
        String name = Bookmark.findName(e);
        if (Bookmark.isBookmarked(name)) {
            currentBookmark = Bookmark.findMatchingBookmark(name);
            eventBus.subscribe(currentBookmark);
        } else
            currentBookmark = new Bookmark(name, null);
    }

    //there's some fuck ass recursion happening here from player.closeHandledScreen() and idrk why
    public static void closeHandledScreen() {
        try {
            Util.notifyAll("Closing gui", Util.notificationTypes.GUI);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                Util.notifyError("Client is null", null);
                return;
            }
            if(!(client.currentScreen instanceof HandledScreen<?>))
                return;


            client.execute(() -> {
                ClientPlayerEntity player = client.player;
                customCloseHandledScreen();
            });
        } catch (Exception e) {
            e.printStackTrace();
            Util.notifyError("Error closing gui", e);
        }
    }

    private static void customCloseHandledScreen() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player == null) {
                Util.notifyError("Player is null, cannot close screen", null);
                return;
            }
            player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(player.currentScreenHandler.syncId));
            client.setScreen(null);
            player.currentScreenHandler = player.playerScreenHandler;

        } catch (Exception e) {
            Util.notifyError("Error encountered while closing screen with custom method", e);
            throw new RuntimeException(e);
        }
    }

    public static void closeSign(){
        try {
            Util.notifyAll("Closing sign", Util.notificationTypes.GUI);
            MinecraftClient mcclient = MinecraftClient.getInstance();
            if (mcclient != null && mcclient.currentScreen instanceof AbstractSignEditScreen signEditScreen) {
                mcclient.execute(signEditScreen::close);
            } else {
                Util.notifyError("Error closing sign: client was null or not in a sign", null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Util.notifyError("Unknown error while closing sign", e);
        }
    }



    public static void setSignText(String text, boolean closeAfter) {
        final int MAX_ATTEMPTS = 5;
        final long DELAY_MS = 200;

        CompletableFuture.runAsync(() -> {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.currentScreen instanceof SignEditScreen) {
                    final SignEditScreen screen = (SignEditScreen) client.currentScreen;
                    client.execute(() -> {
                        try {
                            AccessorSignEditScreen signScreen = (AccessorSignEditScreen) screen;
                            String[] lines = text.split("\n", 4);
                            int originalRow = signScreen.getCurrentRow();

                            for (int i = 0; i < 4; i++) {
                                String line = i < lines.length ? lines[i] : "";
                                signScreen.setCurrentRow(i);
                                signScreen.callSetCurrentRowMessage(line);
                            }
                            signScreen.setCurrentRow(originalRow);
                        } catch (Exception e) {
                            Util.notifyError("Error executing sign text update: " + e.getMessage(), e);
                            e.printStackTrace();
                        }
                    });
                    if (closeAfter)
                        closeSign();
                    return;
                } else {
                    if (attempt < MAX_ATTEMPTS - 1) {
                        try {
                            Thread.sleep(DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            MinecraftClient finalClient = MinecraftClient.getInstance();
                            if (finalClient != null) {
                                finalClient.execute(() -> Util.notifyError("Sign text setting interrupted", null));
                            }
                            return;
                        }
                    }
                }
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> Util.notifyError("Error setting sign text: client was null or not in a sign after " + MAX_ATTEMPTS + " attempts", null));
            } else {
                Util.notifyError("Error setting sign text: Failed after " + MAX_ATTEMPTS + " attempts, client was null.", null);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onLoad(ChestLoadedEvent e){
        lowerChestInventory = e.getLowerChestInventory();
        updateFlipGui();
    }

    public static boolean inFlipGui() {
        if (getContainerName() == null) {
            return false;
        }
        return getContainerName().contains("Order options");
    }

    public static void updateFlipGui(){
        if(inFlipGui()) {
            inFlipGui = true;
            Util.notifyAll("In flip gui", Util.notificationTypes.GUI);
        }
        else
            inFlipGui = false;

    }
    public static void clickSlot(int slotIndex, int button) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ClientPlayerEntity player = client.player;

        if (interactionManager == null || player == null) return;

        ScreenHandler screenHandler = player.currentScreenHandler;
        int syncId = screenHandler.syncId;
        Util.tickExecuteLater(1, () -> {
            interactionManager.clickSlot(
                    syncId,
                    slotIndex,
                    button,
                    SlotActionType.PICKUP,
                    player
            );
        });
    }

}
