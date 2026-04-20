package com.github.mkram17.bazaarutils.events.screen.handlers;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenInitializedEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

@Module
public class ChestLoadedEventHandler extends BUListener {

    private static final int MAX_ATTEMPTS = 50;

    // < = higher priority (skyblockapi impl detail)
    @Subscription(priority = Integer.MIN_VALUE)
    public void onScreenInit(ScreenInitializedEvent event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) return;
        if (!(container.getMenu() instanceof ChestMenu chest)) return;

        final AtomicInteger attempts = new AtomicInteger(0);
        final Minecraft client = Minecraft.getInstance();

        Runnable checkGuiLoaded = new Runnable() {
            @Override
            public void run() {
                if (client.screen != container) return;

                Container inventory = chest.getContainer();
                if (!inventory.isEmpty()
                        && !inventory.getItem(inventory.getContainerSize() - 1).isEmpty()
                        && !isItemLoading(inventory)) {

                    Component titleComponent = container.getTitle();
                    String title = Util.removeFormatting(titleComponent.getString());
                    Integer rowCount = chest.getRowCount();

                    List<Slot> slots = List.copyOf(chest.slots);
                    List<Slot> containerSlots = chest.slots.stream()
                            .filter(slot -> !(slot.container instanceof Inventory))
                            .toList();
                    List<ItemStack> containerItems = containerSlots.stream()
                            .map(Slot::getItem)
                            .toList();

                    new ChestLoadedEvent(container, titleComponent, title, rowCount, slots, containerSlots, containerItems).post(EVENT_BUS);
                } else if (attempts.getAndIncrement() < MAX_ATTEMPTS) {
                    Util.tickExecuteLater(1, this);
                }
            }
        };

        Util.tickExecuteLater(1, checkGuiLoaded);
    }

    private static boolean isItemLoading(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item.isEmpty()) continue;

            Component customName = item.get(DataComponents.CUSTOM_NAME);
            if (customName == null) continue;

            String name = Util.removeFormatting(customName.getString());
            if (name.contains("Loading")) return true;

            if (name.contains("Sell")) {
                ItemLore lore = item.get(DataComponents.LORE);

                if (lore != null && !lore.lines().isEmpty()) {
                    for (Component line : lore.lines()) {
                        if (Util.removeFormatting(line.getString()).contains("Loading")) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}