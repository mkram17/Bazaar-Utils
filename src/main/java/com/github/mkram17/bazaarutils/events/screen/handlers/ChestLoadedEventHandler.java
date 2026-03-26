package com.github.mkram17.bazaarutils.events.screen.handlers;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenInitializedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

@Module
public class ChestLoadedEventHandler extends BUListener {

    private static final int MAX_ATTEMPTS = 50;

    @Subscription()
    public void onScreenInit(ScreenInitializedEvent event) {
        if (!(event.getScreen() instanceof ContainerScreen container)) return;

        final AtomicInteger attempts = new AtomicInteger(0);
        final Minecraft client = Minecraft.getInstance();

        Runnable checkGuiLoaded = new Runnable() {
            @Override
            public void run() {
                if (client.screen != container) return;

                AbstractContainerMenu menu = container.getMenu();

                if (menu instanceof ChestMenu chest) {
                    Container inventory = chest.getContainer();

                    if (!inventory.isEmpty() && !inventory.getItem(inventory.getContainerSize() - 1).isEmpty() && !isItemLoading(inventory)) {
                        new ChestLoadedEvent(container, inventory, getChestItemSlots(inventory), ContainerManager.getContainerName()).post(EVENT_BUS);
                    } else if (attempts.getAndIncrement() < MAX_ATTEMPTS) {
                        Util.tickExecuteLater(1, this);
                    }
                }
            }
        };

        Util.tickExecuteLater(1, checkGuiLoaded);
    }

    private static List<ItemStack> getChestItemSlots(Container inventory) {
        List<ItemStack> stacks = new ArrayList<>();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);

            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }

        return stacks;
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