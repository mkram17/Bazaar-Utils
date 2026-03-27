package com.github.mkram17.bazaarutils.utils.minecraft.gui.container;

import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;

import java.util.Optional;

// Utility class for current screen info
public class ContainerManager {
    public static void onChestLoaded(ChestLoadedEvent event) {
        lowerChestInventory = event.getLowerChestInventory();
    }

    public static String getContainerName() {
        Optional<ScreenContext> context = ScreenManager.getInstance().current();

        if (context.isEmpty() || context.get().screen().getTitle() == null) {
            return null;
        }

        return Util.removeFormatting(context.get().screen().getTitle().getString());
    }

    @Getter
    private static Container lowerChestInventory = null;

    public static int getLowerChestInventorySize() {
        Container inventory = getLowerChestInventory();

        if (inventory == null) {
            return -1;
        }

        return inventory.getContainerSize();
    }

    public static void clickSlot(int slotIndex, int button) {
        Optional<AbstractContainerMenu> handlerOpt = ScreenManager.getCurrentScreenHandler(AbstractContainerMenu.class);

        Minecraft client = Minecraft.getInstance();

        MultiPlayerGameMode interactionManager = client.gameMode;
        LocalPlayer player = client.player;

        if (interactionManager == null || player == null || handlerOpt.isEmpty()) {
            return;
        }

        int syncId = handlerOpt.get().containerId;

        Util.tickExecuteLater(1, () -> interactionManager
                .handleInventoryMouseClick(syncId,
                        slotIndex,
                        button,
                        ClickType.PICKUP,
                        player
                )
        );
    }

    public static ItemInfo getChestItem(int chestSlot) {
        return SlotLookup.getInventoryItem(lowerChestInventory, chestSlot);
    }

    public static int getInventorySlotFromItemStack(Container lowerChestInventory, ItemStack itemStack) {
        return SlotLookup.getInventorySlotFromItemStack(lowerChestInventory, itemStack).orElse(-1);
    }
}