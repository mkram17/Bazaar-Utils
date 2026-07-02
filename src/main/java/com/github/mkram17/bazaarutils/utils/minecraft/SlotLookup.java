package com.github.mkram17.bazaarutils.utils.minecraft;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SlotLookup {
    public static ItemInfo getInventoryItem(Container inventory, int chestSlot) {
        return new ItemInfo(chestSlot, inventory.getItem(chestSlot));
    }

    public static Optional<ItemInfo> getInventoryItem(Container inventory, BazaarSlots.BazaarSlot slot) {
        return slot.query(inventory)
                .first(inventory)
                .filter(item -> !item.isEmpty());
    }

    public static Optional<Integer> getInventorySlotFromItemStack(Container inventory, ItemStack wanted) {
        for (int i = 0; i < inventory.getContainerSize() - 1; i++) {
            ItemStack item = inventory.getItem(i);

            if (item.isEmpty()) continue;

            if (item == wanted || (ItemStack.isSameItem(item, wanted) && item.getCount() == wanted.getCount())) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }

    public static Optional<Integer> findScreenSlotByProductId(String productId) {
        return ScreenManager.getInstance()
                .current()
                .flatMap(context -> context.as(ContainerScreen.class))
                .flatMap(screen -> {
                    int containerSize = screen.getMenu().getContainer().getContainerSize();
                    List<ItemStack> mainStacks = Objects.requireNonNull(Minecraft.getInstance().player)
                            .getInventory().getNonEquipmentItems();

                    for (int i = 0; i < mainStacks.size(); i++) {
                        ItemStack stack = mainStacks.get(i);

                        boolean matches = !stack.isEmpty()
                                && BazaarDataUtil.findProductIdOptional(stack.getHoverName().getString())
                                .map(id -> id.equals(productId))
                                .orElse(false);

                        if (!matches) continue;

                        int screenSlot = (i < 9)
                                ? containerSize + 27 + i
                                : containerSize + (i - 9);

                        return Optional.of(screenSlot);
                    }

                    return Optional.empty();
                });
    }


    @FunctionalInterface
    public interface IndexReference {
        ContainerQuery query(Container container);

        static IndexReference fixed(int index) {
            return ignored -> ContainerQuery.at(index);
        }

        static IndexReference negativeOffset(int delta) {
            return container -> ContainerQuery.at(container.getContainerSize() - 1 - delta);
        }

        static IndexReference range(int min, int max) {
            return ignored -> ContainerQuery.range(min, max);
        }
    }
}
