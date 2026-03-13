package com.github.mkram17.bazaarutils.utils.minecraft.gui.container;

import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

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
    private static Inventory lowerChestInventory = null;

    public static int getLowerChestInventorySize() {
        Inventory inventory = getLowerChestInventory();

        if (inventory == null) {
            return -1;
        }

        return inventory.size();
    }

    public static void clickSlot(int slotIndex, int button) {
        Optional<ScreenHandler> handlerOpt = ScreenManager.getCurrentScreenHandler(ScreenHandler.class);

        MinecraftClient client = MinecraftClient.getInstance();

        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ClientPlayerEntity player = client.player;

        if (interactionManager == null || player == null || handlerOpt.isEmpty()) {
            return;
        }

        int syncId = handlerOpt.get().syncId;

        Util.tickExecuteLater(1, () -> interactionManager
                .clickSlot(syncId,
                        slotIndex,
                        button,
                        SlotActionType.PICKUP,
                        player
                )
        );
    }

    public static ItemInfo getChestItem(int chestSlot) {
        return SlotLookup.getInventoryItem(lowerChestInventory, chestSlot);
    }

    public static int getInventorySlotFromItemStack(Inventory lowerChestInventory, ItemStack itemStack) {
        return SlotLookup.getInventorySlotFromItemStack(lowerChestInventory, itemStack).orElse(-1);
    }
}