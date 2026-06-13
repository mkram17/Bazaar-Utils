package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.SignOpenEvent;
import com.github.mkram17.bazaarutils.features.Bookmark;
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;

import java.util.function.Consumer;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//TODO make inBazaar() work all the time
public class GUIUtils {
    @Getter @Setter
    private static guiTypes guiType;
    @Getter @Setter
    private static Container lowerChestInventory;

    @RunOnInit
    public static void subscribe() {
        EVENT_BUS.subscribe(GUIUtils.class);
    }

    public enum guiTypes {CHEST, SIGN}


    @RunOnInit
    public static void registerScreenEvent(){
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            lowerChestInventory = null;
            ScreenInfo.initialize(screen);
        });
    }

    public static AbstractContainerMenu getHandledScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return null;
        return client.player.containerMenu;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private static void loadSign(SignOpenEvent e){
        guiType = guiType.SIGN;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private static void setUpBookmark(ChestLoadedEvent e){
        if(!ScreenInfo.getCurrentScreenInfo().inMenu(ScreenInfo.BazaarMenuType.INDIVIDUAL_ITEM))
            return;
        String name = Bookmark.findItemName(e);
        if (Bookmark.isItemBookmarked(name)) {
            Bookmark.findMatchingBookmark(name).get().subscribe();
        } else {
            new Bookmark(name);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    private static void onLoad(ChestLoadedEvent e){
        guiType = guiType.CHEST;
        lowerChestInventory = e.getLowerChestInventory();
    }

    public static void closeHandledScreen() {
        try {
            PlayerActionUtil.notifyAll("Closing gui", Util.notificationTypes.GUI);
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                Util.notifyError("Client is null", new Throwable());
                return;
            }
            if(!(client.screen instanceof AbstractContainerScreen<?>))
                return;

            client.execute(GUIUtils::customCloseHandledScreen);
        } catch (Exception e) {
            Util.notifyError("Error closing gui", e);
        }
    }

    private static void customCloseHandledScreen() {
        try {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null) {
                Util.notifyError("Player is null, cannot close screen", new Throwable());
                return;
            }
            player.connection.send(new ServerboundContainerClosePacket(player.containerMenu.containerId));
            client.setScreen(null);
            player.containerMenu = player.inventoryMenu;

        } catch (Exception e) {
            Util.notifyError("Error encountered while closing screen with custom method", e);
            throw new RuntimeException(e);
        }
    }

    public static void runOnNextSignOpen(Consumer<SignOpenEvent> action) {
        BazaarUtils.EVENT_BUS.subscribe(new Object() {
            @EventHandler
            private void onSignOpen(SignOpenEvent event) {
                try {
                    action.accept(event);
                } finally {
                    BazaarUtils.EVENT_BUS.unsubscribe(this);
                }
            }
        });
    }

    public static void closeSign(){
        try {
            PlayerActionUtil.notifyAll("Closing sign", Util.notificationTypes.GUI);
            Minecraft mcclient = Minecraft.getInstance();
            if (mcclient != null && mcclient.screen instanceof AbstractSignEditScreen signEditScreen) {
                mcclient.execute(signEditScreen::onClose);
            } else {
                Util.notifyError("Error closing sign: client was null or not in a sign", new Throwable());
            }
        } catch (Exception e) {
            Util.notifyError("Unknown error while closing sign", e);
        }
    }

    public static void setSignText(String text, boolean closeAfter) {
        setSignTextInternal(text, closeAfter, 5);
    }

    private static void setSignTextInternal(String text, boolean closeAfter, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            Util.notifyError("Failed to set sign text: Screen not available.", new Throwable());
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            Util.notifyError("Failed to set sign text: MinecraftClient is null.", new Throwable());
            return;
        }

        client.execute(() -> {
            if (client.screen instanceof SignEditScreen screen) {
                try {
                    AccessorSignEditScreen signScreen = (AccessorSignEditScreen) screen;
                    String[] lines = text.split("\n", 4);
                    int originalRow = signScreen.getLine();

                    for (int i = 0; i < 4; i++) {
                        String line = i < lines.length ? lines[i] : "";
                        signScreen.setLine(i);
                        signScreen.callSetMessage(line);
                    }
                    signScreen.setLine(originalRow);

                    if (closeAfter) {
                        closeSign();
                    }
                } catch (Exception e) {
                    Util.notifyError("Error executing sign text update", e);
                    e.printStackTrace();
                }
            } else {
                // Screen not open yet, schedule a retry
                Util.tickExecuteLater(4, () -> setSignTextInternal(text, closeAfter, attemptsLeft - 1));
            }
        });
    }

    public static void clickSlot(int slotIndex, int button) {
        Minecraft client = Minecraft.getInstance();
        MultiPlayerGameMode interactionManager = client.gameMode;
        LocalPlayer player = client.player;

        if (interactionManager == null || player == null) return;

        AbstractContainerMenu screenHandler = player.containerMenu;
        int syncId = screenHandler.containerId;
        Util.tickExecuteLater(1, () -> {
            interactionManager.handleInventoryMouseClick(
                    syncId,
                    slotIndex,
                    button,
                    ClickType.PICKUP,
                    player
            );
        });
    }
    public static ItemStack getChestItem(int chestSlot) {
        if (guiType != guiTypes.CHEST) return ItemStack.EMPTY;
        if (lowerChestInventory == null) return ItemStack.EMPTY;
        if (chestSlot < 0 || chestSlot >= lowerChestInventory.getContainerSize()) return ItemStack.EMPTY;
        ItemStack stack = lowerChestInventory.getItem(chestSlot);
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
