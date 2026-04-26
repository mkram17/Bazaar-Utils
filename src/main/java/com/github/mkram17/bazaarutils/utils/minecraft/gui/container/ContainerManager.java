package com.github.mkram17.bazaarutils.utils.minecraft.gui.container;

import com.github.mkram17.bazaarutils.events.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

// Utility class for current screen info
public class ContainerManager {
    @Nullable
    private static AbstractContainerScreen<ChestMenu> screen = null;

    @Nullable
    private static Container container = null;

    @Nullable
    private static String title = null;

    @Nullable
    private static Component titleComponent = null;

    @Getter
    @NotNull
    private static List<Slot> containerSlots = List.of();

    @Getter
    @NotNull
    private static List<Slot> playerSlots = List.of();

    public static void onContainerLoaded(ContainerLoadedEvent event) {
        screen = event.getScreen();
        container = event.getContainer();
        title = event.getTitle();
        titleComponent = event.getTitleComponent();
        containerSlots = event.getContainerSlots();
        playerSlots = event.getPlayerSlots();
    }

    public static Optional<Container> getContainer() {
        return Optional.ofNullable(container);
    }

    public static Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public static Optional<Component> getTitleComponent() {
        return Optional.ofNullable(titleComponent);
    }

    public static Optional<AbstractContainerScreen<ChestMenu>> getScreen() {
        return Optional.ofNullable(screen);
    }

    public static int getContainerSize() {
        return container != null ? container.getContainerSize() : -1;
    }

    public static ItemInfo getContainerItem(int chestSlot) {
        return getContainer()
                .map(container -> SlotLookup.getInventoryItem(container, chestSlot))
                .orElse(ItemInfo.empty(chestSlot));
    }

    public static Optional<Integer> getContainerSlotOf(ItemStack wanted) {
        return getContainer()
                .flatMap(container -> SlotLookup.getInventorySlotFromItemStack(container, wanted));
    }

    public static void clickSlot(int slotIndex, int button) {
        Optional<AbstractContainerMenu> menu = ScreenManager.getMenu(AbstractContainerMenu.class);

        Minecraft client = Minecraft.getInstance();
        MultiPlayerGameMode interactionManager = client.gameMode;

        LocalPlayer player = client.player;

        if (interactionManager == null || player == null || menu.isEmpty()) return;

        int syncId = menu.get().containerId;

        Util.tickExecuteLater(1, () ->
                interactionManager.handleInventoryMouseClick(syncId, slotIndex, button, ClickType.PICKUP, player)
        );
    }
}