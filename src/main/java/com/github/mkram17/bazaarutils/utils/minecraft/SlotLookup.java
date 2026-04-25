package com.github.mkram17.bazaarutils.utils.minecraft;

import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerQuery;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class SlotLookup {
    public static ItemInfo getInventoryItem(Container inventory, int chestSlot) {
        return new ItemInfo(chestSlot, inventory.getItem(chestSlot));
    }

    public static ItemInfo getInventoryItem(Container inventory, BazaarSlots.BazaarSlot slot) {
        return slot.query(inventory).first(inventory).orElse(ItemInfo.empty(-1));
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
