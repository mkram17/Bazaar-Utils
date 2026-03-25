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
        return getInventoryItem(inventory, slot.resolve(inventory));
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


    public sealed interface IndexReference permits IndexReference.FixedIndex, IndexReference.ContainerSizeNegativeOffset {
        int resolve(Container container);

        default int getMaxInventoryIndex(Container container) {
            return container.getContainerSize() - 1;
        }

        default ContainerQuery query(Container container) {
            return ContainerQuery.at(resolve(container));
        }

        final class FixedIndex implements IndexReference {
            private final int index;

            public FixedIndex(int index) {
                this.index = index;
            }

            @Override
            public int resolve(Container container) {
                return index;
            }
        }

        final class ContainerSizeNegativeOffset implements IndexReference {
            private final int delta;

            public ContainerSizeNegativeOffset(int delta) {
                this.delta = delta;
            }

            @Override
            public int resolve(Container container) {
                return this.getMaxInventoryIndex(container) - delta;
            }
        }
    }
}
