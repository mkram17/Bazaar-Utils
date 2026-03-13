package com.github.mkram17.bazaarutils.utils.minecraft;

import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerQuery;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import java.util.Optional;

public class SlotLookup {
    public static ItemInfo getInventoryItem(Inventory inventory, int chestSlot) {
        return new ItemInfo(chestSlot, inventory.getStack(chestSlot));
    }

    public static ItemInfo getInventoryItem(Inventory inventory, BazaarSlots.BazaarSlot slot) {
        return getInventoryItem(inventory, slot.resolve(inventory));
    }

    public static Optional<Integer> getInventorySlotFromItemStack(Inventory inventory, ItemStack wanted) {
        for (int i = 0; i < inventory.size() - 1; i++) {
            ItemStack item = inventory.getStack(i);

            if (item.isEmpty()) continue;

            if (item == wanted || (ItemStack.areItemsEqual(item, wanted) && item.getCount() == wanted.getCount())) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }


    public sealed interface IndexReference permits IndexReference.FixedIndex, IndexReference.ContainerSizeNegativeOffset {
        int resolve(Inventory container);

        default int getMaxInventoryIndex(Inventory container) {
            return container.size() - 1;
        }

        default ContainerQuery query(Inventory container) {
            return ContainerQuery.at(resolve(container));
        }

        final class FixedIndex implements IndexReference {
            private final int index;

            public FixedIndex(int index) {
                this.index = index;
            }

            @Override
            public int resolve(Inventory container) {
                return index;
            }
        }

        final class ContainerSizeNegativeOffset implements IndexReference {
            private final int delta;

            public ContainerSizeNegativeOffset(int delta) {
                this.delta = delta;
            }

            @Override
            public int resolve(Inventory container) {
                return this.getMaxInventoryIndex(container) - delta;
            }
        }
    }
}
